package br.com.familhas.crm.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.api.BusinessException;

/** Contrato com a OpenAI Images API sem rede: MockRestServiceServer. */
class OpenAiImageGeneratorTests {

    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };
    private static final String URL = "https://openai.test/v1/images/generations";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://openai.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiImageGenerator generator = new OpenAiImageGenerator(builder.build(), "sk-segredo",
            new OpenAiProperties("", "gpt-image-1", "1024x1536", "medium", Duration.ofSeconds(5), 20),
            new ObjectMapper());

    @Test
    void sendsOneJpegRequestWithTheKeyInTheHeaderAndDecodesTheImage() {
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-segredo"))
                .andExpect(jsonPath("$.model").value("gpt-image-1"))
                .andExpect(jsonPath("$.prompt").value("Maceio beach"))
                .andExpect(jsonPath("$.n").value(1))
                .andExpect(jsonPath("$.size").value("1024x1536"))
                .andExpect(jsonPath("$.quality").value("medium"))
                .andExpect(jsonPath("$.output_format").value("jpeg"))
                .andRespond(withSuccess("{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(JPEG)
                        + "\"}]}", MediaType.APPLICATION_JSON));

        assertThat(generator.generate("Maceio beach")).isEqualTo(JPEG);
        server.verify();
    }

    @Test
    void moderationBlockIsARefusalTheTeamCanAct() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"moderation_blocked\",\"message\":\"blocked\"}}"));
        assertThatThrownBy(() -> generator.generate("x")).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.code()).isEqualTo("AI_IMAGE_REFUSED");
            assertThat(e.getStatusCode().value()).isEqualTo(422);
        });
    }

    @Test
    void otherFailuresAreBadGatewayWithoutLeakingTheKey() {
        server.expect(requestTo(URL)).andRespond(withUnauthorizedRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"invalid_api_key\"}}"));
        assertThatThrownBy(() -> generator.generate("x")).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.code()).isEqualTo("AI_IMAGE_FAILED");
            assertThat(e.getStatusCode().value()).isEqualTo(502);
            assertThat(e.getMessage()).doesNotContain("sk-segredo");
        });

        server.reset();
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> generator.generate("x")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("AI_IMAGE_FAILED"));
    }

    @Test
    void answerWithoutImageIsAFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> generator.generate("x")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("AI_IMAGE_FAILED"));

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[{\"b64_json\":\"%%%\"}]}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> generator.generate("x")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("AI_IMAGE_FAILED"));
    }
}
