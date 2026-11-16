package br.com.familhas.crm.instagram;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publicação no Instagram pela Meta Graph API (ADR 0016). Credenciais em {@code familhas.integrations.meta-*}.
 *
 * @param graphBaseUrl      versão da Graph API
 * @param maxCarouselItems  limite de itens de carrossel aceito pela API da Meta (o editor permite até 20 lâminas)
 * @param mediaLinkTtl      validade dos links assinados que a Meta baixa
 * @param containerTimeout  espera máxima pelo processamento de vídeo do Reel
 * @param stuckAfter        publicação presa (servidor caiu) é resolvida depois deste tempo
 */
@ConfigurationProperties("familhas.meta")
public record MetaProperties(
        @DefaultValue("https://graph.facebook.com/v23.0") String graphBaseUrl,
        @DefaultValue("10") int maxCarouselItems,
        @DefaultValue("PT1H") Duration mediaLinkTtl,
        @DefaultValue("PT5M") Duration containerTimeout,
        @DefaultValue("PT30M") Duration stuckAfter) {
}
