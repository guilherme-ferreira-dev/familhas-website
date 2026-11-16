package br.com.familhas.crm.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Contrato com a Graph API sem rede. O token vai no corpo ou no header, nunca na URL. */
class GraphMetaGatewayTests {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.test/v23.0");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GraphMetaGateway gateway = new GraphMetaGateway(builder.build(), "17841400000", "token-secreto",
            new ObjectMapper());

    @Test
    void createsContainersPublishesAndReadsThePermalinkWithoutTokenInUrls() {
        server.expect(requestTo("https://graph.test/v23.0/17841400000/media")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(Matchers.allOf(Matchers.containsString("media_type=CAROUSEL"),
                        Matchers.containsString("children=1%2C2"), Matchers.containsString("access_token=token-secreto"))))
                .andRespond(withSuccess("{\"id\":\"c-9\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.test/v23.0/c-9?fields=status_code"))
                .andExpect(header("Authorization", "Bearer token-secreto"))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.test/v23.0/17841400000/media_publish"))
                .andExpect(content().string(Matchers.containsString("creation_id=c-9")))
                .andRespond(withSuccess("{\"id\":\"m-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graph.test/v23.0/m-1?fields=permalink"))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/abc/\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.createContainer(Map.of("media_type", "CAROUSEL", "children", "1,2"))).isEqualTo("c-9");
        assertThat(gateway.containerStatus("c-9")).isEqualTo("FINISHED");
        assertThat(gateway.publish("c-9")).isEqualTo("m-1");
        assertThat(gateway.permalink("m-1")).isEqualTo("https://www.instagram.com/p/abc/");
        server.verify();
    }

    @Test
    void metaErrorsKeepTheCodeAndNeverTheToken() {
        server.expect(requestTo(Matchers.endsWith("/media"))).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"Error validating access token\",\"code\":190}}"));
        server.expect(requestTo(Matchers.containsString("fields=permalink"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> gateway.createContainer(Map.of("image_url", "https://x/y.jpg")))
                .isInstanceOfSatisfying(MetaApiException.class, e -> {
                    assertThat(e.tokenExpired()).isTrue();
                    assertThat(e.getMessage()).doesNotContain("token-secreto");
                });
        assertThat(gateway.permalink("m-2")).as("permalink e melhor esforco").isNull();
    }
}
