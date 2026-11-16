package br.com.familhas.crm;

import static br.com.familhas.crm.CrmTestTokens.crm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditLogRepository;

/**
 * Fundação do CRM com Oracle real (prompt mestre, Etapa 2; ADR 0014): V18 validada pelo Hibernate, upload com
 * permissão por categoria e tipo pelos bytes, link assinado, exclusão e auditoria.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.admin.payment-feed.enabled=false",
        "familhas.storage.local-root=${java.io.tmpdir}/familhas-crm-it" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrmFoundationIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static final byte[] PDF = ("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF " + UUID.randomUUID())
            .getBytes(StandardCharsets.ISO_8859_1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void attendantUploadsAVoucherSourceAndDownloadsItByASignedLink() throws Exception {
        JsonNode file = json(mockMvc.perform(multipart("/api/crm/files").file(pdf("../orcamento hotel.pdf"))
                        .param("category", "VOUCHER_SOURCE").with(crm(CrmRole.ATENDENTE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("orcamento hotel.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.sha256").value(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(PDF)))));
        String id = file.get("id").asText();

        JsonNode link = json(mockMvc.perform(post("/api/crm/files/{id}/link", id).with(crm(CrmRole.ATENDENTE)))
                .andExpect(status().isOk()));
        String path = link.get("path").asText();

        // Sem token: vale só a assinatura.
        byte[] downloaded = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);

        mockMvc.perform(get(path.replaceFirst("expires=\\d+", "expires=4102444800"))).andExpect(status().isForbidden());
        mockMvc.perform(get(path.replace(id, UUID.randomUUID().toString()))).andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll()).anySatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.DOWNLOAD);
            assertThat(row.getResourceId()).isEqualTo(id);
            assertThat(row.getActorUsername()).isEqualTo("atendente.crm");
        }).anySatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(row.getResource()).isEqualTo("crm/files");
            assertThat(row.getResourceId()).isEqualTo(id);
        }).noneSatisfy(row -> assertThat(row.getPath()).endsWith("/link").satisfies(
                p -> assertThat(row.getAction()).isEqualTo(AuditAction.CREATE)));
    }

    @Test
    void categoryDecidesWhoCanUploadReadAndDelete() throws Exception {
        // Marketing não mexe em vouchers; atendente não exclui; gestor exclui.
        mockMvc.perform(multipart("/api/crm/files").file(pdf("v.pdf")).param("category", "VOUCHER_SOURCE")
                .with(crm(CrmRole.MARKETING))).andExpect(status().isForbidden());
        String id = json(mockMvc.perform(multipart("/api/crm/files").file(pdf("v.pdf"))
                .param("category", "VOUCHER_SOURCE").with(crm(CrmRole.ATENDENTE)))
                .andExpect(status().isCreated())).get("id").asText();

        mockMvc.perform(get("/api/crm/files/{id}", id).with(crm(CrmRole.MARKETING))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/crm/files/{id}/link", id).with(crm(CrmRole.MARKETING)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/crm/files").param("category", "VOUCHER_SOURCE").with(crm(CrmRole.MARKETING)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/crm/files/{id}", id).with(crm(CrmRole.ATENDENTE))).andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll()).anySatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.DELETE);
            assertThat(row.getResourceId()).isEqualTo(id);
            assertThat(row.getStatus()).as("negado e auditado como 403, nao 500").isEqualTo(403);
        });
        mockMvc.perform(delete("/api/crm/files/{id}", id).with(crm(CrmRole.GESTOR))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/crm/files/{id}", id).with(crm(CrmRole.GESTOR)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void contentIsCheckedByItsBytesNotByNameOrDeclaredType() throws Exception {
        mockMvc.perform(multipart("/api/crm/files")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PDF))
                        .param("category", "DOCUMENT").with(crm(CrmRole.ATENDENTE)))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("FILE_TYPE_MISMATCH"));
        mockMvc.perform(multipart("/api/crm/files")
                        .file(new MockMultipartFile("file", "relatorio.pdf", "application/pdf",
                                "MZ\u0090\u0000 executavel".getBytes(StandardCharsets.ISO_8859_1)))
                        .param("category", "DOCUMENT").with(crm(CrmRole.ATENDENTE)))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
        mockMvc.perform(multipart("/api/crm/files")
                        .file(new MockMultipartFile("file", "x.svg", "image/svg+xml",
                                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                        .param("category", "POST_IMAGE").with(crm(CrmRole.MARKETING)))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/api/crm/files").file(new MockMultipartFile("file", "vazio.pdf", "application/pdf",
                        new byte[0])).param("category", "DOCUMENT").with(crm(CrmRole.ATENDENTE)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_EMPTY"));
    }

    @Test
    void documentsAreSearchableByNameWithoutCase() throws Exception {
        String marker = "Contrato-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(multipart("/api/crm/files").file(pdf(marker + ".pdf")).param("category", "DOCUMENT")
                .with(crm(CrmRole.ATENDENTE))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/crm/files").param("category", "DOCUMENT").param("name", marker.toLowerCase())
                        .with(crm(CrmRole.MARKETING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(marker + ".pdf"));
    }

    @Test
    void settingsShowCompanyDataAndIntegrationsWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/crm/settings").with(crm(CrmRole.MARKETING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.legalName").value("EEBPG Agência de Viagens LTDA"))
                .andExpect(jsonPath("$.company.cnpj").value("62.845.580/0001-75"))
                .andExpect(jsonPath("$.company.tradeName").value("Voe Familhas"))
                .andExpect(jsonPath("$.integrations.length()").value(7))
                .andExpect(jsonPath("$.integrations[?(@.configured == true)]").isEmpty());
    }

    @Test
    void managerReadsTheAuditLogButAttendantDoesNot() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").with(crm(CrmRole.GESTOR))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/audit-logs").with(crm(CrmRole.ATENDENTE))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/clients").with(crm(CrmRole.GESTOR))).andExpect(status().isForbidden());
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
