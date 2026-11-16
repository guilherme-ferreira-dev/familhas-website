package br.com.familhas.crm.instagram;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de geração por IA (JSON, "Criar posts Insta"): ideia de até 2.000 caracteres (com foco: 1 post; sem foco:
 * pauta semanal), até 10 imagens de referência, link de post como inspiração (nunca reaproveita preços, datas ou
 * condições, nunca cita o concorrente), carrossel com exatamente {@code slides} lâminas (padrão 5, máximo 20) e o
 * modo automático por cidade (7 lâminas).
 */
public record InstagramGenerateRequest(
        @NotNull PostFormat format,
        @Size(max = 2000) String idea,
        @Size(max = 80) String city,
        @Min(1) @Max(20) Integer slides,
        @Size(max = 10) List<@NotNull UUID> referenceFileIds,
        @Size(max = 300) String inspirationUrl) {

    public int slideCount() {
        if (city != null && !city.isBlank()) {
            return 7;
        }
        return slides == null ? 5 : slides;
    }
}
