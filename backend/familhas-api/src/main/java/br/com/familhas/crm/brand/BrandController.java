package br.com.familhas.crm.brand;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Identidade visual: {@code /api/crm/brand} (ADR 0016). */
@RestController
@RequestMapping("/api/crm/brand")
public class BrandController {

    private final BrandService service;

    public BrandController(BrandService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:dashboard:ler')")
    public BrandResponse get() {
        return service.get();
    }

    /** Troca a logo oficial. Permissão de criar no painel (admin e gestor), conferida no upload. */
    @PutMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('crm:dashboard:criar')")
    public BrandResponse replaceLogo(@RequestPart("file") MultipartFile file) {
        return service.replaceLogo(file);
    }
}
