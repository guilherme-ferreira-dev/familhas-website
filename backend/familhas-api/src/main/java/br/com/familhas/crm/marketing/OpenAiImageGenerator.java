package br.com.familhas.crm.marketing;

import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.api.BusinessException;

/**
 * OpenAI Images API ({@code POST /v1/images/generations}), gpt-image-1, uma imagem JPEG por chamada. A chave vai só
 * no cabeçalho e nunca para log; os erros registram o status e o código da OpenAI, não o prompt.
 */
public class OpenAiImageGenerator implements ImageGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiImageGenerator.class);
    static final String MODERATION_BLOCKED = "moderation_blocked";
    /** Teto do que aceitamos de volta (uma JPEG 1024x1536 fica bem abaixo disso). */
    static final int MAX_BYTES = 15 * 1024 * 1024;

    private final RestClient client;
    private final String apiKey;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiImageGenerator(RestClient client, String apiKey, OpenAiProperties properties,
            ObjectMapper objectMapper) {
        this.client = client;
        this.apiKey = apiKey;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] generate(String prompt) {
        JsonNode body;
        try {
            body = client.post().uri("/v1/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.imageModel(),
                            "prompt", prompt,
                            "n", 1,
                            "size", properties.imageSize(),
                            "quality", properties.imageQuality(),
                            "output_format", "jpeg"))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            String code = errorCode(e.getResponseBodyAsString());
            log.warn("OpenAI recusou a imagem: status={} code={}", e.getStatusCode().value(), code);
            if (MODERATION_BLOCKED.equals(code)) {
                throw refused();
            }
            throw failed();
        } catch (RestClientException e) {
            log.warn("OpenAI falhou ao gerar imagem ({})", e.getClass().getSimpleName());
            throw failed();
        }
        return decode(body);
    }

    static byte[] decode(JsonNode body) {
        JsonNode b64 = body == null ? null : body.path("data").path(0).get("b64_json");
        if (b64 == null || !b64.isTextual() || b64.asText().isEmpty()
                || b64.asText().length() > MAX_BYTES / 3 * 4 + 4) {
            log.warn("OpenAI respondeu sem imagem utilizavel");
            throw failed();
        }
        try {
            return Base64.getDecoder().decode(b64.asText());
        } catch (IllegalArgumentException e) {
            log.warn("OpenAI respondeu com base64 invalido");
            throw failed();
        }
    }

    private String errorCode(String body) {
        try {
            JsonNode code = objectMapper.readTree(body).path("error").path("code");
            return code.isTextual() ? code.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static BusinessException refused() {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_IMAGE_REFUSED",
                "A IA recusou gerar essa imagem. Tente outro nome ou envie uma foto.");
    }

    static BusinessException failed() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "AI_IMAGE_FAILED",
                "A IA de imagem nao respondeu. Tente de novo em instantes ou envie uma foto.");
    }
}
