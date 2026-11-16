package br.com.familhas.crm.flightsearch;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Pesquisar Voos (ADR 0015).
 *
 * @param origins           origens fixas oferecidas na tela (IATA); a primeira é a padrão. Inclui CXJ (Caxias do Sul)
 * @param serpapiBaseUrl    endpoint da SerpApi (fixo; não vem do usuário)
 * @param timeout           tempo máximo de cada chamada à SerpApi
 * @param maxCallsPerSearch teto de chamadas à SerpApi numa pesquisa (custo): pesquisa maior é recusada
 * @param flexibleStepDays  intervalo entre as datas de ida testadas no modo flexível
 * @param searchesPerHour   pesquisas por pessoa por hora (consumo da SerpApi)
 * @param cacheTtl          cache de resultado por trecho e datas
 */
@ConfigurationProperties("familhas.flight-search")
public record FlightSearchProperties(
        @DefaultValue({ "BSB", "CXJ" }) List<String> origins,
        @DefaultValue("https://serpapi.com") String serpapiBaseUrl,
        @DefaultValue("PT20S") Duration timeout,
        @DefaultValue("40") int maxCallsPerSearch,
        @DefaultValue("7") int flexibleStepDays,
        @DefaultValue("30") int searchesPerHour,
        @DefaultValue("PT30M") Duration cacheTtl) {
}
