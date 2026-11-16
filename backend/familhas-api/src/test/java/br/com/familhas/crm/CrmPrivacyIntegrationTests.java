package br.com.familhas.crm;

import static br.com.familhas.crm.CrmTestTokens.crm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.familhas.admin.audit.AuditLogRepository;
import br.com.familhas.crm.backup.BackupServiceAccess;

/**
 * LGPD com Oracle real (prompt mestre, Etapa 6; ADR 0018): relatório de titular, anonimização com prévia que
 * respeita viagem em andamento e prazo legal do voucher, auditoria sem o CPF inteiro e registro de incidentes.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.admin.payment-feed.enabled=false",
        "familhas.storage.local-root=${java.io.tmpdir}/familhas-crm-it6" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrmPrivacyIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    /** CPF válido só deste teste (outros testes usam 529.982.247-25). */
    private static final String CPF = "11144477735";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void reportAndAnonymizationRespectOngoingTripsAndLegalRetention() throws Exception {
        String past = flight("Titular Passado", LocalDateTime.now().minusDays(40));
        String future = flight("Titular Futuro", LocalDateTime.now().plusDays(20));
        String recentVoucher = voucher("Titular Voucher Recente");
        String oldVoucher = voucher("Titular Voucher Antigo");
        jdbc.update("update vouchers set issued_at = ? where id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusYears(6), BackupServiceAccess.raw(UUID.fromString(oldVoucher)));

        String request = "{\"cpf\":\"111.444.777-35\",\"identityVerified\":true,\"reference\":\"Pedido 2026-001 por e-mail\"}";
        JsonNode report = json(send(post("/api/crm/privacy/subject-report").contentType(MediaType.APPLICATION_JSON)
                .content(request), CrmRole.ADMIN).andExpect(status().isOk()));
        assertThat(report.get("flights")).hasSize(2);
        assertThat(report.get("vouchers")).hasSize(2);

        JsonNode preview = json(send(post("/api/crm/privacy/anonymize").contentType(MediaType.APPLICATION_JSON)
                .content(request), CrmRole.ADMIN).andExpect(status().isOk()));
        assertThat(preview.get("applied").asBoolean()).isFalse();
        assertThat(preview.get("travelersAnonymized").asInt()).isEqualTo(1);
        assertThat(preview.get("vouchersAnonymized").asInt()).isEqualTo(1);
        assertThat(preview.get("kept").findValuesAsText("reason"))
                .containsExactlyInAnyOrder("viagem ainda não terminou", "prazo legal de retenção");
        send(get("/api/crm/client-flights/{id}", past), CrmRole.ADMIN)
                .andExpect(jsonPath("$.travelers[0].fullName").value("Titular Passado"));

        send(post("/api/crm/privacy/anonymize").param("apply", "true").contentType(MediaType.APPLICATION_JSON)
                .content(request), CrmRole.ADMIN).andExpect(jsonPath("$.applied").value(true));
        send(get("/api/crm/client-flights/{id}", past), CrmRole.ADMIN)
                .andExpect(jsonPath("$.travelers[0].fullName").value("Titular anonimizado"))
                .andExpect(jsonPath("$.travelers[0].cpfMasked").doesNotExist())
                .andExpect(jsonPath("$.travelers[0].phone").doesNotExist());
        send(get("/api/crm/client-flights/{id}", future), CrmRole.ADMIN)
                .andExpect(jsonPath("$.travelers[0].fullName").value("Titular Futuro"));
        send(get("/api/crm/vouchers/{id}", oldVoucher), CrmRole.ADMIN)
                .andExpect(jsonPath("$.clientName").value("Titular anonimizado"))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.details.passengers").doesNotExist());
        send(get("/api/crm/vouchers/{id}", recentVoucher), CrmRole.ADMIN)
                .andExpect(jsonPath("$.clientName").value("Titular Voucher Recente"));

        // Depois: o relatório só acha o que ficou; a auditoria tem o protocolo e o CPF mascarado, nunca inteiro.
        JsonNode after = json(send(post("/api/crm/privacy/subject-report").contentType(MediaType.APPLICATION_JSON)
                .content(request), CrmRole.ADMIN));
        assertThat(after.get("flights")).hasSize(1);
        assertThat(after.get("vouchers")).hasSize(1);
        assertThat(auditLogRepository.findAll()).filteredOn(r -> "crm/privacy".equals(r.getResource()))
                .hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(r -> assertThat(r.getDetails()).contains("***.444.777-**").contains("Pedido 2026-001")
                        .doesNotContain(CPF));
    }

    @Test
    void onlyAdminsWithIdentityConfirmedCanAct() throws Exception {
        String ok = "{\"cpf\":\"111.444.777-35\",\"identityVerified\":true,\"reference\":\"P1\"}";
        send(post("/api/crm/privacy/subject-report").contentType(MediaType.APPLICATION_JSON).content(ok), CrmRole.GESTOR)
                .andExpect(status().isForbidden());
        send(post("/api/crm/privacy/subject-report").contentType(MediaType.APPLICATION_JSON)
                .content(ok.replace("true", "false")), CrmRole.ADMIN).andExpect(status().isBadRequest());
        send(post("/api/crm/privacy/subject-report").contentType(MediaType.APPLICATION_JSON)
                .content(ok.replace("111.444.777-35", "111.111.111-11")), CrmRole.ADMIN)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CPF_INVALID"));
    }

    @Test
    void incidentsFollowThePhasesOfTheResponsePlan() throws Exception {
        Instant detected = Instant.now().minusSeconds(3600);
        ObjectNode body = objectMapper.createObjectNode().put("title", "Link de documento compartilhado por engano")
                .put("description", "Link assinado enviado ao grupo errado; venceu em 5 minutos.")
                .put("severity", "MEDIA").put("status", "ENCERRADO").put("personalData", true)
                .put("dataCategories", "nome, CPF").put("affectedEstimate", 1).put("detectedAt", detected.toString());
        send(post("/api/crm/privacy/incidents").contentType(MediaType.APPLICATION_JSON).content(body.toString()),
                CrmRole.ADMIN).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLOSED_AT_REQUIRED"));
        body.put("status", "ABERTO");
        JsonNode created = json(send(post("/api/crm/privacy/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()), CrmRole.ADMIN).andExpect(status().isCreated()));
        body.put("status", "CONTIDO").put("containedAt", detected.plusSeconds(600).toString())
                .put("version", created.get("version").asLong());
        send(put("/api/crm/privacy/incidents/{id}", created.get("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()), CrmRole.ADMIN).andExpect(jsonPath("$.status").value("CONTIDO"));
        send(get("/api/crm/privacy/incidents"), CrmRole.ADMIN).andExpect(jsonPath("$[0].title")
                .value("Link de documento compartilhado por engano"));
        send(get("/api/crm/privacy/incidents"), CrmRole.GESTOR).andExpect(status().isForbidden());
    }

    private String flight(String passenger, LocalDateTime outbound) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("airline", "AZUL").put("origin", "BSB")
                .put("destination", "SSA").put("outboundAt", outbound.withNano(0).toString());
        body.withArray("travelers").addObject().put("fullName", passenger).put("cpf", CPF).put("phone", "61999990000");
        return json(send(post("/api/crm/client-flights").contentType(MediaType.APPLICATION_JSON).content(body.toString()),
                CrmRole.ATENDENTE).andExpect(status().isCreated())).get("id").asText();
    }

    private String voucher(String name) throws Exception {
        return json(send(post("/api/crm/vouchers").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"TOUR","clientName":"%s","clientCpf":"%s","clientPhone":"61999990000","notes":"pediu cadeirinha",
                 "details":{"description":"Buggy","serviceDate":"2026-01-10","passengers":["Filho do Titular"]}}"""
                .formatted(name, CPF)), CrmRole.ATENDENTE).andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, CrmRole role) throws Exception {
        return mockMvc.perform(request.with(crm(role)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
