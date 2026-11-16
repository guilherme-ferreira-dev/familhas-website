package br.com.familhas.crm.brand;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.crm.file.CrmFileResponse;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileCategory;

/**
 * Logo oficial (JSON: sem fundo azul e sem slogan, aplicada por composição em todos os posts, nunca recriada pela
 * IA). PNG ou WEBP, para manter a transparência. Trocar a logo apaga o arquivo anterior.
 */
@Service
public class BrandService {

    private final CrmBrandRepository repository;
    private final CrmFileService files;

    public BrandService(CrmBrandRepository repository, CrmFileService files) {
        this.repository = repository;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public BrandResponse get() {
        return new BrandResponse(repository.findById(CrmBrandEntity.SINGLE_ID)
                .filter(b -> b.getLogoFileId() != null)
                .flatMap(b -> files.findAttached(FileCategory.BRAND_ASSET, List.of(b.getLogoFileId())).stream()
                        .findFirst())
                .orElse(null));
    }

    @Transactional
    public BrandResponse replaceLogo(MultipartFile file) {
        CrmFileResponse stored = files.upload(FileCategory.BRAND_ASSET, file);
        CrmBrandEntity brand = repository.findById(CrmBrandEntity.SINGLE_ID).orElseGet(CrmBrandEntity::new);
        java.util.UUID previous = brand.getLogoFileId();
        brand.setLogoFileId(stored.id());
        brand.setUpdatedBy(AuditActor.current().displayName());
        repository.saveAndFlush(brand);
        if (previous != null) {
            files.delete(previous);
        }
        return new BrandResponse(stored);
    }
}
