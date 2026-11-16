package br.com.familhas.crm.file;

import java.util.EnumSet;
import java.util.Set;

import br.com.familhas.crm.CrmResource;

/**
 * Para que serve o arquivo. Decide a permissão ({@code crm:<recurso>:<ação>}) e os tipos aceitos. Todo arquivo
 * é da agência (uma só); quem pode ler o recurso pode ler o arquivo, e só isso.
 */
public enum FileCategory {
    DOCUMENT(CrmResource.DOCUMENTS,
            EnumSet.of(StoredFileType.PDF, StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP)),
    FLIGHT_ATTACHMENT(CrmResource.CLIENT_FLIGHTS,
            EnumSet.of(StoredFileType.PDF, StoredFileType.JPEG, StoredFileType.PNG)),
    VOUCHER_SOURCE(CrmResource.VOUCHERS, EnumSet.of(StoredFileType.PDF, StoredFileType.JPEG, StoredFileType.PNG)),
    /** Imagens e PDFs de orçamento dos posts de cotação e de cruzeiros. */
    POST_IMAGE(CrmResource.QUOTE_POSTS,
            EnumSet.of(StoredFileType.PDF, StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP)),
    INSTAGRAM_MEDIA(CrmResource.INSTAGRAM_POSTS,
            EnumSet.of(StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP, StoredFileType.MP4)),
    /** Fotos do roteiro e o PDF do orçamento importado. */
    ITINERARY_PHOTO(CrmResource.ITINERARIES,
            EnumSet.of(StoredFileType.PDF, StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP)),
    /**
     * Logo oficial (PNG/WEBP com transparência). Toda a equipe lê (painel, posts); só quem cria no painel troca
     * (admin e gestor).
     */
    BRAND_ASSET(CrmResource.DASHBOARD, EnumSet.of(StoredFileType.PNG, StoredFileType.WEBP));

    private final CrmResource resource;
    private final Set<StoredFileType> allowedTypes;

    FileCategory(CrmResource resource, Set<StoredFileType> allowedTypes) {
        this.resource = resource;
        this.allowedTypes = allowedTypes;
    }

    public CrmResource resource() {
        return resource;
    }

    public Set<StoredFileType> allowedTypes() {
        return allowedTypes;
    }
}
