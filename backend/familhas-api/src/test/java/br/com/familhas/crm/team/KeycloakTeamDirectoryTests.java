package br.com.familhas.crm.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Contrato com a Admin API do Keycloak sem rede: client credentials, criação de usuário e erros. */
class KeycloakTeamDirectoryTests {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KeycloakTeamDirectory directory = new KeycloakTeamDirectory(builder.build(),
            new TeamProperties("https://kc.test", "voe-familhas-crm", "familhas-crm-team", "http://crm.test/",
                    Duration.ofHours(72)), "segredo", Clock.systemUTC());

    @Test
    void usesClientCredentialsOnceAndCreatesTheUser() {
        server.expect(requestTo("https://kc.test/realms/voe-familhas-crm/protocol/openid-connect/token"))
                .andExpect(content().string(Matchers.allOf(Matchers.containsString("grant_type=client_credentials"),
                        Matchers.containsString("client_secret=segredo"))))
                .andRespond(withSuccess("{\"access_token\":\"tk\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        HttpHeaders location = new HttpHeaders();
        location.setLocation(URI.create("https://kc.test/admin/realms/voe-familhas-crm/users/abc-123"));
        server.expect(requestTo("https://kc.test/admin/realms/voe-familhas-crm/users")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tk"))
                .andExpect(content().string(Matchers.containsString("\"requiredActions\":[\"UPDATE_PASSWORD\"]")))
                .andRespond(withStatus(HttpStatus.CREATED).headers(location));
        server.expect(requestTo(Matchers.startsWith("https://kc.test/admin/realms/voe-familhas-crm/users/abc-123/execute-actions-email?lifespan=259200&client_id=familhas-crm&redirect_uri=http%3A%2F%2Fcrm.test%2F")))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("Authorization", "Bearer tk"))
                .andRespond(withSuccess());

        String id = directory.create(new TeamDirectory.User(null, "ana@x.com", "ana@x.com", "Ana", "S", true, null,
                List.of("UPDATE_PASSWORD")));
        directory.sendActionsEmail(id, List.of("UPDATE_PASSWORD"));
        assertThat(id).isEqualTo("abc-123");
        server.verify();
    }

    @Test
    void conflictAndRefusedSecretBecomeTypedErrors() {
        server.expect(requestTo(Matchers.endsWith("/token"))).andRespond(withSuccess("{\"access_token\":\"tk\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.endsWith("/users"))).andRespond(withStatus(HttpStatus.CONFLICT)
                .body("{\"errorMessage\":\"User exists with same username\"}"));
        assertThatThrownBy(() -> directory.create(new TeamDirectory.User(null, "a", "a", "A", "B", true, null, List.of())))
                .isInstanceOfSatisfying(KeycloakAdminException.class, e -> assertThat(e.status()).isEqualTo(409));

        RestClient.Builder other = RestClient.builder();
        MockRestServiceServer refused = MockRestServiceServer.bindTo(other).build();
        refused.expect(requestTo(Matchers.endsWith("/token"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        KeycloakTeamDirectory wrong = new KeycloakTeamDirectory(other.build(), new TeamProperties("https://kc.test",
                "voe-familhas-crm", "familhas-crm-team", "http://crm.test/", Duration.ofHours(1)), "errado", Clock.systemUTC());
        assertThatThrownBy(() -> wrong.user("x")).isInstanceOfSatisfying(KeycloakAdminException.class,
                e -> assertThat(e.getMessage()).doesNotContain("errado"));
    }
}
