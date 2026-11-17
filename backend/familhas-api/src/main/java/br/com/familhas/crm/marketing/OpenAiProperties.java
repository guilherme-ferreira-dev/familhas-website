package br.com.familhas.crm.marketing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * IA de imagem pela OpenAI (ADR 0019). A chave fica em {@code familhas.integrations.openai-api-key}; aqui só o que
 * não é segredo.
 *
 * @param baseUrl       endpoint da OpenAI (fixo; não vem do usuário)
 * @param imageModel    modelo de imagem
 * @param imageSize     1024x1536 (retrato): o post é 4:5 e o canvas corta o excesso
 * @param imageQuality  low, medium ou high: decide o custo de cada imagem
 * @param timeout       tempo máximo de uma geração (a OpenAI leva dezenas de segundos)
 * @param imagesPerHour gerações por pessoa por hora (custo)
 */
@ConfigurationProperties("familhas.openai")
public record OpenAiProperties(
        @DefaultValue("https://api.openai.com") String baseUrl,
        @DefaultValue("gpt-image-1") String imageModel,
        @DefaultValue("1024x1536") String imageSize,
        @DefaultValue("medium") String imageQuality,
        @DefaultValue("PT120S") Duration timeout,
        @DefaultValue("20") int imagesPerHour) {
}
