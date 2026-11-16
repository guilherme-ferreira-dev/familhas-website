package br.com.familhas.crm.team;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Admin API do Keycloak com client credentials da conta de serviço do realm do CRM. O token fica em memória até
 * 30 s antes de vencer. O segredo nunca vai para log nem para a resposta.
 */
public class KeycloakTeamDirectory implements TeamDirectory {

    private final RestClient client;
    private final TeamProperties properties;
    private final String clientSecret;
    private final Clock clock;
    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakTeamDirectory(RestClient client, TeamProperties properties, String clientSecret, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    public List<User> users() {
        List<User> users = new ArrayList<>();
        for (JsonNode node : get("/users?first=0&max=500&briefRepresentation=false")) {
            if (!node.path("username").asText("").startsWith("service-account-")) {
                users.add(user(node));
            }
        }
        return users;
    }

    @Override
    public User user(String id) {
        return user(get("/users/" + id));
    }

    @Override
    public Set<String> realmRoles(String userId) {
        Set<String> roles = new HashSet<>();
        get("/users/" + userId + "/role-mappings/realm").forEach(r -> roles.add(r.path("name").asText()));
        return roles;
    }

    @Override
    public boolean hasOtp(String userId) {
        for (JsonNode credential : get("/users/" + userId + "/credentials")) {
            if ("otp".equals(credential.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String create(User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.username());
        body.put("email", user.email());
        body.put("firstName", user.firstName());
        body.put("lastName", user.lastName());
        body.put("enabled", true);
        body.put("emailVerified", true);
        body.put("requiredActions", user.requiredActions());
        try {
            URI location = client.post().uri(admin("/users")).headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
                    .getHeaders().getLocation();
            if (location == null) {
                throw new KeycloakAdminException(500, "Keycloak nao devolveu o usuario criado");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    @Override
    public void addRealmRoles(String userId, Set<String> roles) {
        List<Map<String, Object>> available = pick("/users/" + userId + "/role-mappings/realm/available", roles);
        if (!available.isEmpty()) {
            send("POST", "/users/" + userId + "/role-mappings/realm", available);
        }
    }

    @Override
    public void removeRealmRoles(String userId, Set<String> roles) {
        List<Map<String, Object>> current = pick("/users/" + userId + "/role-mappings/realm", roles);
        if (!current.isEmpty()) {
            send("DELETE", "/users/" + userId + "/role-mappings/realm", current);
        }
    }

    @Override
    public void setEnabled(String userId, boolean enabled) {
        send("PUT", "/users/" + userId, Map.of("enabled", enabled));
    }

    @Override
    public void setRequiredActions(String userId, List<String> actions) {
        send("PUT", "/users/" + userId, Map.of("requiredActions", actions));
    }

    @Override
    public void sendActionsEmail(String userId, List<String> actions) {
        String query = "?lifespan=" + properties.inviteLifespan().toSeconds() + "&client_id=familhas-crm&redirect_uri="
                + java.net.URLEncoder.encode(properties.crmUrl(), java.nio.charset.StandardCharsets.UTF_8);
        send("PUT", "/users/" + userId + "/execute-actions-email" + query, actions);
    }

    @Override
    public void logout(String userId) {
        send("POST", "/users/" + userId + "/logout", null);
    }

    /** Representações (id e nome) dos papéis pedidos, lidas das listas do próprio usuário. */
    private List<Map<String, Object>> pick(String path, Set<String> names) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode node : get(path)) {
            if (names.contains(node.path("name").asText())) {
                list.add(Map.of("id", node.path("id").asText(), "name", node.path("name").asText()));
            }
        }
        return list;
    }

    private JsonNode get(String path) {
        try {
            return client.get().uri(admin(path)).headers(h -> h.setBearerAuth(token())).retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    private void send(String method, String path, Object body) {
        try {
            var spec = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(admin(path))
                    .headers(h -> h.setBearerAuth(token()));
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            spec.retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    /** URI já codificada: o RestClient não codifica de novo (o redirect_uri do convite saía como %253A). */
    private URI admin(String path) {
        return URI.create(properties.keycloakUrl() + "/admin/realms/" + properties.realm() + path);
    }

    private synchronized String token() {
        if (token != null && clock.instant().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", clientSecret);
        try {
            JsonNode response = client.post()
                    .uri(URI.create(properties.keycloakUrl() + "/realms/" + properties.realm()
                            + "/protocol/openid-connect/token"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            token = response.path("access_token").asText();
            tokenExpiresAt = clock.instant().plusSeconds(response.path("expires_in").asLong(60));
            return token;
        } catch (RestClientResponseException e) {
            throw new KeycloakAdminException(e.getStatusCode().value(), "Conta de servico da equipe recusada pelo Keycloak");
        }
    }

    private static User user(JsonNode node) {
        List<String> actions = new ArrayList<>();
        node.path("requiredActions").forEach(a -> actions.add(a.asText()));
        return new User(node.path("id").asText(), node.path("username").asText(null),
                node.path("email").asText(null), node.path("firstName").asText(null),
                node.path("lastName").asText(null), node.path("enabled").asBoolean(false),
                node.hasNonNull("createdTimestamp") ? node.get("createdTimestamp").asLong() : null, actions);
    }

    private static KeycloakAdminException translate(RestClientResponseException e) {
        String message = e.getResponseBodyAsString();
        return new KeycloakAdminException(e.getStatusCode().value(),
                message.length() > 200 ? message.substring(0, 200) : message);
    }
}
