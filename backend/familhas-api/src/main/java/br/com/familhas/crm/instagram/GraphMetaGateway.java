package br.com.familhas.crm.instagram;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Graph API com o token do servidor. O token vai no corpo (form), nunca na URL nem em log; as mensagens de erro
 * guardadas são as da Meta, sem o token.
 */
public class GraphMetaGateway implements MetaGateway {

    private final RestClient client;
    private final String instagramUserId;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    public GraphMetaGateway(RestClient client, String instagramUserId, String accessToken, ObjectMapper objectMapper) {
        this.client = client;
        this.instagramUserId = instagramUserId;
        this.accessToken = accessToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public String createContainer(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        return post("/" + instagramUserId + "/media", form).path("id").asText();
    }

    @Override
    public String containerStatus(String containerId) {
        return get("/" + containerId + "?fields=status_code").path("status_code").asText();
    }

    @Override
    public String publish(String containerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", containerId);
        return post("/" + instagramUserId + "/media_publish", form).path("id").asText();
    }

    @Override
    public String permalink(String mediaId) {
        try {
            JsonNode node = get("/" + mediaId + "?fields=permalink");
            return node.hasNonNull("permalink") ? node.get("permalink").asText() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        form.add("access_token", accessToken);
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    private JsonNode get(String pathAndQuery) {
        try {
            // GET da Graph API exige o token; vai no header Authorization, não na URL.
            return client.get().uri(pathAndQuery).header("Authorization", "Bearer " + accessToken).retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    private MetaApiException translate(RestClientResponseException e) {
        try {
            JsonNode error = objectMapper.readTree(e.getResponseBodyAsString()).path("error");
            String message = error.path("error_user_msg").asText(error.path("message").asText("Erro da Meta"));
            return new MetaApiException(error.path("code").asInt(e.getStatusCode().value()),
                    message.length() > 300 ? message.substring(0, 300) : message);
        } catch (Exception parse) {
            return new MetaApiException(e.getStatusCode().value(), "Erro HTTP " + e.getStatusCode().value() + " da Meta");
        }
    }
}
