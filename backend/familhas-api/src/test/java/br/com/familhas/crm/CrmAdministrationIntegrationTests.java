package br.com.familhas.crm;

import static br.com.familhas.crm.CrmTestTokens.crm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditLogRepository;
import br.com.familhas.crm.team.TeamDirectory;

/**
 * Administração do CRM com Oracle real (prompt mestre, Etapa 5; ADR 0017): backup lógico, restauração que nunca
 * sobrescreve, backup guardado criptografado com aviso por e-mail, e equipe com a Admin API do Keycloak dublada.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.admin.payment-feed.enabled=false",
        "familhas.integrations.keycloak-team-secret=segredo-de-teste",
        "spring.mail.host=smtp.teste",
        "familhas.backup.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "familhas.storage.local-root=${java.io.tmpdir}/familhas-crm-it5" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrmAdministrationIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogRepository;
    @MockitoBean private TeamDirectory directory;
    @MockitoBean private JavaMailSender mailSender;

    // ---------- Backup e restauração ----------

    @Test
    void restoreBringsBackDeletedRecordsAndNeverOverwritesLaterEdits() throws Exception {
        String flight = createFlight("Restaurada Silva");
        String voucher = json(send(post("/api/crm/vouchers").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"TOUR","clientName":"Cliente Backup","clientCpf":"529.982.247-25",
                 "details":{"description":"Buggy","serviceDate":"2027-01-30"}}"""), CrmRole.ATENDENTE)
                .andExpect(status().isCreated())).get("id").asText();
        JsonNode itinerary = json(send(post("/api/crm/itineraries").contentType(MediaType.APPLICATION_JSON)
                .content(itineraryBody("Antes do backup", null)), CrmRole.ATENDENTE).andExpect(status().isCreated()));

        byte[] backup = send(get("/api/crm/backups/export"), CrmRole.GESTOR).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode file = objectMapper.readTree(backup);
        assertThat(file.get("format").asText()).isEqualTo("voe-familhas-crm-backup");
        assertThat(file.get("sha256").asText()).hasSize(64);
        assertThat(new String(backup, StandardCharsets.UTF_8)).contains("52998224725");

        // Depois do backup: o voo e o voucher somem; o roteiro é editado.
        send(delete("/api/crm/client-flights/{id}", flight), CrmRole.GESTOR).andExpect(status().isNoContent());
        jdbcDeleteVoucher(voucher);
        send(put("/api/crm/itineraries/{id}", itinerary.get("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content(itineraryBody("Editado depois", itinerary.get("version").asLong())), CrmRole.ATENDENTE)
                .andExpect(status().isOk());

        // Prévia não grava nada.
        JsonNode preview = json(send(multipart("/api/crm/backups/restore").file(file(backup)), CrmRole.ADMIN)
                .andExpect(status().isOk()));
        assertThat(preview.get("applied").asBoolean()).isFalse();
        assertThat(table(preview, "Voos Clientes").get("restored").asInt()).isEqualTo(1);
        send(get("/api/crm/client-flights/{id}", flight), CrmRole.ADMIN).andExpect(status().isNotFound());

        JsonNode applied = json(send(multipart("/api/crm/backups/restore").file(file(backup)).param("apply", "true"),
                CrmRole.ADMIN).andExpect(status().isOk()));
        assertThat(table(applied, "Voos Clientes").get("restored").asInt()).isEqualTo(1);
        assertThat(table(applied, "Vouchers").get("restored").asInt()).isEqualTo(1);
        assertThat(table(applied, "Roteiros").get("kept").asInt()).isGreaterThanOrEqualTo(1);

        send(get("/api/crm/client-flights/{id}", flight), CrmRole.ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.travelers[0].fullName").value("Restaurada Silva"))
                .andExpect(jsonPath("$.travelers[0].cpfMasked").value("***.982.247-**"));
        send(get("/api/crm/vouchers/{id}", voucher), CrmRole.ADMIN).andExpect(status().isOk());
        send(get("/api/crm/itineraries/{id}", itinerary.get("id").asText()), CrmRole.ADMIN)
                .andExpect(jsonPath("$.observations").value("Editado depois"));

        // De novo: nada a restaurar (ids originais).
        JsonNode again = json(send(multipart("/api/crm/backups/restore").file(file(backup)).param("apply", "true"),
                CrmRole.ADMIN).andExpect(status().isOk()));
        assertThat(again.get("tables").findValuesAsText("restored")).allMatch("0"::equals);
        assertThat(auditLogRepository.findAll()).anySatisfy(row -> assertThat(row.getAction()).isEqualTo(AuditAction.RESTORE))
                .anySatisfy(row -> assertThat(row.getAction()).isEqualTo(AuditAction.EXPORT));
    }

    @Test
    void tamperedForeignOrUnauthorizedRestoresAreRefused() throws Exception {
        byte[] backup = send(get("/api/crm/backups/export"), CrmRole.ADMIN).andReturn().getResponse()
                .getContentAsByteArray();
        ObjectNode tampered = (ObjectNode) objectMapper.readTree(backup);
        ((ObjectNode) tampered.get("tables")).putArray("vouchers").addObject().put("id", UUID.randomUUID().toString());
        send(multipart("/api/crm/backups/restore").file(file(objectMapper.writeValueAsBytes(tampered))), CrmRole.ADMIN)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BACKUP_CHECKSUM"));
        send(multipart("/api/crm/backups/restore").file(file("{\"format\":\"outro\"}".getBytes())), CrmRole.ADMIN)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BACKUP_INVALID"));
        send(multipart("/api/crm/backups/restore").file(file("não é json".getBytes(StandardCharsets.UTF_8))), CrmRole.ADMIN)
                .andExpect(jsonPath("$.code").value("BACKUP_INVALID"));

        send(multipart("/api/crm/backups/restore").file(file(backup)), CrmRole.GESTOR).andExpect(status().isForbidden());
        send(get("/api/crm/backups/export"), CrmRole.ATENDENTE).andExpect(status().isForbidden());
        send(get("/api/crm/backups/export"), CrmRole.MARKETING).andExpect(status().isForbidden());
    }

    @Test
    void emailCarriesOnlyALinkAndTheStoredBackupIsEncrypted() throws Exception {
        createFlight("Email Souza");
        JsonNode stored = json(send(post("/api/crm/backups/email").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), CrmRole.GESTOR).andExpect(status().isOk()));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("voefamilhas@gmail.com");
        assertThat(sent.getValue().getText()).contains("backup?baixar=" + stored.get("id").asText())
                .doesNotContain("52998224725").doesNotContain("Email Souza");

        byte[] onDisk = java.nio.file.Files.walk(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "familhas-crm-it5")).filter(java.nio.file.Files::isRegularFile)
                .map(p -> { try { return java.nio.file.Files.readAllBytes(p); } catch (Exception e) { return new byte[0]; } })
                .filter(b -> b.length > 100).findFirst().orElseThrow();
        assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("voe-familhas-crm-backup");

        byte[] downloaded = send(get("/api/crm/backups/{id}/download", stored.get("id").asText()), CrmRole.ADMIN)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(downloaded, StandardCharsets.UTF_8)).contains("Email Souza");
        send(get("/api/crm/backups"), CrmRole.GESTOR).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emailedTo").value("voefamilhas@gmail.com"));
        send(post("/api/crm/backups/email").contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"nao-e-email\"}"),
                CrmRole.GESTOR).andExpect(status().isBadRequest());
    }

    // ---------- Equipe ----------

    @Test
    void onlyAdminsManageTheTeamAndManagersOnlyRead() throws Exception {
        String id = "44444444-4444-4444-4444-444444444444";
        TeamDirectory.User carla = new TeamDirectory.User(id, "carla@x.com", "carla@x.com", "Carla", "Lima", true,
                1_700_000_000_000L, List.of("UPDATE_PASSWORD"));
        when(directory.users()).thenReturn(List.of(carla));
        when(directory.create(any())).thenReturn(id);
        when(directory.user(id)).thenReturn(carla);
        when(directory.realmRoles(id)).thenReturn(Set.of("crm-marketing"));

        send(get("/api/crm/team"), CrmRole.GESTOR).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("MARKETING"))
                .andExpect(jsonPath("$[0].pendingActions[0]").value("UPDATE_PASSWORD"));
        String invite = "{\"email\":\"carla@x.com\",\"firstName\":\"Carla\",\"lastName\":\"Lima\",\"role\":\"MARKETING\"}";
        send(post("/api/crm/team").contentType(MediaType.APPLICATION_JSON).content(invite), CrmRole.GESTOR)
                .andExpect(status().isForbidden());
        send(post("/api/crm/team").contentType(MediaType.APPLICATION_JSON).content(invite), CrmRole.ADMIN)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("carla@x.com"));
        send(get("/api/crm/team"), CrmRole.ATENDENTE).andExpect(status().isForbidden());
        assertThat(auditLogRepository.findAll()).anySatisfy(row -> {
            assertThat(row.getResource()).isEqualTo("crm/team");
            assertThat(row.getAction()).isEqualTo(AuditAction.CREATE);
        });
    }

    // ---------- apoio ----------

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcDeleteVoucher(String id) {
        // Vouchers não têm exclusão pela API (só cancelamento): a perda aqui simula um apagamento indevido no banco.
        jdbc.update("delete from vouchers where id = ?", br.com.familhas.crm.backup.BackupServiceAccess.raw(UUID.fromString(id)));
    }

    private String createFlight(String passenger) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("airline", "LATAM").put("origin", "BSB")
                .put("destination", "MCZ").put("outboundAt", LocalDateTime.now().plusDays(9).withNano(0).toString());
        body.withArray("travelers").addObject().put("fullName", passenger).put("cpf", "529.982.247-25");
        return json(send(post("/api/crm/client-flights").contentType(MediaType.APPLICATION_JSON).content(body.toString()),
                CrmRole.ATENDENTE).andExpect(status().isCreated())).get("id").asText();
    }

    private static String itineraryBody(String observations, Long version) {
        return "{\"destination\":\"Maceió\",\"days\":2,\"observations\":\"" + observations + "\","
                + (version == null ? "" : "\"version\":" + version + ",")
                + "\"sections\":{\"overview\":\"Mar verde\",\"days\":[],\"included\":[]}}";
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "backup.json", "application/json", content);
    }

    private static JsonNode table(JsonNode report, String name) {
        for (JsonNode table : report.get("tables")) {
            if (table.get("table").asText().equals(name)) {
                return table;
            }
        }
        throw new AssertionError("tabela " + name);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, CrmRole role) throws Exception {
        return mockMvc.perform(request.with(crm(role)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
