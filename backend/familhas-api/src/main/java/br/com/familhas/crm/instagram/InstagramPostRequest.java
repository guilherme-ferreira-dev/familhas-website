package br.com.familhas.crm.instagram;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Criar ou editar post. Imagens já compostas no navegador (logo no topo, rodapé com WhatsApp, gancho em destaque)
 * e enviadas como arquivos INSTAGRAM_MEDIA. {@code version} obrigatório na edição.
 */
public record InstagramPostRequest(
        @NotNull PostFormat format,
        PostOrigin origin,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String idea,
        @Size(max = 2200) String caption,
        @Size(max = 500) String hashtags,
        @Size(max = 20) List<@Valid @NotNull Slide> slides,
        UUID videoFileId,
        Long version) {

    /** Lâmina: título e de 3 a 5 tópicos são recomendados (a tela orienta); a API limita tamanhos. */
    public record Slide(@NotNull UUID imageFileId, UUID cleanFileId, @Size(max = 120) String hook,
            @Size(max = 120) String title, @Size(max = 5) List<@NotNull @Size(max = 200) String> bullets,
            @Size(max = 1000) String caption) {
    }
}
