package br.com.familhas.crm.marketing;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.familhas.api.BusinessException;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileCategory;
import br.com.familhas.crm.instagram.InstagramGenerateRequest;
import br.com.familhas.crm.instagram.InstagramPostRequest;
import br.com.familhas.crm.instagram.PostFormat;
import br.com.familhas.crm.integration.CrmIntegration;
import br.com.familhas.crm.integration.IntegrationNotConfiguredException;
import br.com.familhas.crm.integration.IntegrationRegistry;
import br.com.familhas.crm.outbound.OutboundUrlPolicy;

/**
 * Pontos de IA do marketing (ADR 0016, D5). Valida a entrada inteira (arquivos e links, com a política anti-SSRF)
 * e só então exige o provedor: sem ele, 503 INTEGRATION_NOT_CONFIGURED e a tela segue no modo manual. Nada é
 * simulado; nenhum provedor está implementado.
 *
 * Quando houver provedor, as regras do JSON entram no prompt e na conferência da resposta: destino pela cidade da
 * hospedagem, datas "29 de Jan a 04 de Fev", nunca reaproveitar preço/data/condição do post de inspiração, nunca
 * citar o concorrente, e logo e rodapé sempre compostos no navegador (nunca desenhados pela IA).
 */
@Service
public class MarketingAiService {

    private final IntegrationRegistry integrations;
    private final CrmFileService files;
    private final OutboundUrlPolicy urls;

    public MarketingAiService(IntegrationRegistry integrations, CrmFileService files, OutboundUrlPolicy urls) {
        this.integrations = integrations;
        this.files = files;
        this.urls = urls;
    }

    public InstagramPostRequest generateInstagramPost(InstagramGenerateRequest request) {
        if (request.referenceFileIds() != null) {
            request.referenceFileIds().forEach(id -> requireCategory(id, FileCategory.INSTAGRAM_MEDIA));
        }
        if (request.inspirationUrl() != null && !request.inspirationUrl().isBlank()) {
            String host = urls.check(request.inspirationUrl()).getHost().toLowerCase(Locale.ROOT);
            if (!host.equals("instagram.com") && !host.endsWith(".instagram.com")) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "URL_NOT_ALLOWED",
                        "Use um link de post do Instagram.");
            }
        }
        integrations.require(CrmIntegration.AI_TEXT);
        integrations.require(CrmIntegration.AI_IMAGE);
        if (request.format() == PostFormat.REEL) {
            integrations.require(CrmIntegration.AI_VIDEO);
        }
        throw notImplemented(CrmIntegration.AI_TEXT);
    }

    /** Lê o orçamento (PDF enviado como POST_IMAGE, ou link) de pacote ou de cruzeiro. */
    public void extractQuote(QuoteSource source) {
        boolean hasUrl = source.url() != null && !source.url().isBlank();
        if (source.fileId() == null && !hasUrl) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_REQUIRED",
                    "Envie o PDF ou informe o link do orcamento.");
        }
        if (source.fileId() != null) {
            requireCategory(source.fileId(), FileCategory.POST_IMAGE);
        }
        if (hasUrl) {
            urls.check(source.url());
        }
        integrations.require(CrmIntegration.AI_TEXT);
        throw notImplemented(CrmIntegration.AI_TEXT);
    }

    /** Imagem da cidade da hospedagem ou do navio (sempre substituível por upload). */
    public void generateImage(ImageRequest request) {
        integrations.require(CrmIntegration.AI_IMAGE);
        throw notImplemented(CrmIntegration.AI_IMAGE);
    }

    /** Roteiro a partir de cidade e dias, ou do PDF do orçamento (ITINERARY_PHOTO). */
    public void generateItinerary(ItineraryAiRequest request) {
        if (request.fileId() != null) {
            requireCategory(request.fileId(), FileCategory.ITINERARY_PHOTO);
        }
        integrations.require(CrmIntegration.AI_TEXT);
        throw notImplemented(CrmIntegration.AI_TEXT);
    }

    private void requireCategory(UUID fileId, FileCategory category) {
        if (files.categoryOf(fileId) != category) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_CATEGORY_MISMATCH",
                    "Esse arquivo nao pode ser usado aqui.");
        }
    }

    /** Provedor marcado como configurado, mas sem adapter no código: continua indisponível, sem fingir. */
    private static IntegrationNotConfiguredException notImplemented(CrmIntegration integration) {
        return new IntegrationNotConfiguredException(integration);
    }
}
