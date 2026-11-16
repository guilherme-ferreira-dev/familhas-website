package br.com.familhas.crm;

import static br.com.familhas.crm.CrmTestTokens.crm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
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
import br.com.familhas.common.BusinessTime;
import br.com.familhas.crm.flightsearch.FlightOffer;
import br.com.familhas.crm.flightsearch.FlightSearchGateway;

/**
 * Viagens do CRM com Oracle real (prompt mestre, Etapa 3; ADR 0015): V19..V21 validadas pelo Hibernate, Voos
 * Clientes (busca sem acento, arquivados, anexos, CPF mascarado, trava otimista), vouchers (número, campos por tipo,
 * documento auditado), pesquisa de voos com a SerpApi trocada por um dublê e o painel.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.admin.payment-feed.enabled=false",
        "familhas.integrations.serpapi-api-key=chave-de-teste",
        "familhas.storage.local-root=${java.io.tmpdir}/familhas-crm-it3" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrmTravelIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static final String VALID_CPF = "52998224725";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogRepository;
    @MockitoBean private FlightSearchGateway flightSearchGateway;

    // ---------- Voos Clientes ----------

    @Test
    void quickSearchIgnoresAccentsAndWordOrderWithinTheSamePassenger() throws Exception {
        String marker = "Zuleica" + UUID.randomUUID().toString().substring(0, 6);
        createFlight("Conceição " + marker + " Araújo", "Pedro Lima", LocalDateTime.now().plusDays(3));

        for (String q : List.of("araujo " + marker.toLowerCase(), marker.toUpperCase() + " CONCEICAO",
                "conceição " + marker)) {
            send(get("/api/crm/client-flights").param("q", q), CrmRole.ATENDENTE).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
        // Palavras de passageiros diferentes no mesmo voo não contam.
        send(get("/api/crm/client-flights").param("q", marker + " pedro"), CrmRole.ATENDENTE)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listIsOrderedByOutboundAndArchivedGoToHistory() throws Exception {
        String airline = "Cia" + UUID.randomUUID().toString().substring(0, 6);
        LocalDateTime base = LocalDateTime.now().plusDays(40).withNano(0);
        String late = createFlight("Ana Tarde", null, base.plusDays(2), airline).get("id").asText();
        String early = createFlight("Bia Cedo", null, base, airline).get("id").asText();

        send(get("/api/crm/client-flights").param("airline", airline), CrmRole.ATENDENTE)
                .andExpect(jsonPath("$.content[0].id").value(early))
                .andExpect(jsonPath("$.content[1].id").value(late))
                .andExpect(jsonPath("$.content[0].passengers[0]").value("Bia Cedo"));

        send(post("/api/crm/client-flights/{id}/archive", early), CrmRole.ATENDENTE).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        send(get("/api/crm/client-flights").param("airline", airline), CrmRole.ATENDENTE)
                .andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/crm/client-flights").param("airline", airline).param("archived", "true"), CrmRole.ATENDENTE)
                .andExpect(jsonPath("$.content[0].id").value(early));
    }

    @Test
    void cpfIsMaskedKeptOnEditAndValidated() throws Exception {
        JsonNode flight = createFlight("Carla Souza", null, LocalDateTime.now().plusDays(10));
        String id = flight.get("id").asText();
        assertThat(flight.at("/travelers/0/cpfMasked").asText()).isEqualTo("***.982.247-**");
        assertThat(flight.toString()).doesNotContain(VALID_CPF);

        // Edição com cpf null: mantém o guardado. A tela só tem o mascarado.
        ObjectNode edit = flightBody("Carla Souza Lima", null, LocalDateTime.now().plusDays(11), "LATAM");
        ((ObjectNode) edit.withArray("travelers").get(0)).put("id", flight.at("/travelers/0/id").asText()).putNull("cpf");
        edit.put("version", flight.get("version").asLong());
        JsonNode edited = json(send(put("/api/crm/client-flights/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(edit.toString()), CrmRole.ATENDENTE).andExpect(status().isOk()));
        assertThat(edited.at("/travelers/0/cpfMasked").asText()).isEqualTo("***.982.247-**");
        assertThat(edited.at("/travelers/0/fullName").asText()).isEqualTo("Carla Souza Lima");

        // A mesma versão de novo: outra pessoa já salvou.
        send(put("/api/crm/client-flights/{id}", id).contentType(MediaType.APPLICATION_JSON).content(edit.toString()),
                CrmRole.ATENDENTE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        ObjectNode invalid = flightBody("Dora", null, LocalDateTime.now().plusDays(5), "GOL");
        ((ObjectNode) invalid.withArray("travelers").get(0)).put("cpf", "111.111.111-11");
        send(post("/api/crm/client-flights").contentType(MediaType.APPLICATION_JSON).content(invalid.toString()),
                CrmRole.ATENDENTE).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CPF_INVALID"));
    }

    @Test
    void attachmentsLiveWithTheFlightAndPermissionsHold() throws Exception {
        String id = createFlight("Eva Prado", null, LocalDateTime.now().plusDays(20)).get("id").asText();
        byte[] pdf = "%PDF-1.4\nbilhete\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        JsonNode withFile = json(send(multipart("/api/crm/client-flights/{id}/attachments", id)
                .file(new MockMultipartFile("file", "bilhete.pdf", "application/pdf", pdf)), CrmRole.ATENDENTE)
                .andExpect(status().isOk()));
        String fileId = withFile.at("/attachments/0/id").asText();
        assertThat(withFile.at("/attachments/0/category").asText()).isEqualTo("FLIGHT_ATTACHMENT");

        send(get("/api/crm/client-flights/{id}", id), CrmRole.MARKETING).andExpect(status().isForbidden());
        send(delete("/api/crm/client-flights/{id}/attachments/{f}", id, fileId), CrmRole.ATENDENTE)
                .andExpect(status().isForbidden());
        send(delete("/api/crm/client-flights/{id}", id), CrmRole.GESTOR).andExpect(status().isNoContent());
        send(get("/api/crm/files/{id}", fileId), CrmRole.GESTOR).andExpect(status().isNotFound());
    }

    // ---------- Vouchers ----------

    @Test
    void voucherIsNumberedValidatedByTypeAndPrintedWithAudit() throws Exception {
        send(post("/api/crm/vouchers").contentType(MediaType.APPLICATION_JSON)
                .content(voucherBody("HOTEL", "{\"provider\":\"Hotel Ponta Verde\"}")), CrmRole.ATENDENTE)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_FIELDS_MISSING"))
                .andExpect(jsonPath("$.fields[0]").value("serviceDate"));

        JsonNode voucher = json(send(post("/api/crm/vouchers").contentType(MediaType.APPLICATION_JSON)
                .content(voucherBody("HOTEL", """
                        {"provider":"Hotel Ponta Verde","serviceDate":"2027-01-29","endDate":"2027-02-04",
                         "roomType":"Duplo","board":"Café da manhã","adults":2}""")), CrmRole.ATENDENTE)
                .andExpect(status().isCreated()));
        String id = voucher.get("id").asText();
        assertThat(voucher.get("number").asText()).matches("VF-\\d{6}");
        assertThat(voucher.get("clientCpfMasked").asText()).isEqualTo("***.982.247-**");
        assertThat(voucher.toString()).doesNotContain(VALID_CPF);

        send(get("/api/crm/vouchers/{id}/document", id), CrmRole.ATENDENTE).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientCpfFormatted").value("529.982.247-25"))
                .andExpect(jsonPath("$.company.cnpj").value("62.845.580/0001-75"))
                .andExpect(jsonPath("$.voucher.details.board").value("Café da manhã"));
        assertThat(auditLogRepository.findAll()).anySatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.DOWNLOAD);
            assertThat(row.getResource()).isEqualTo("crm/vouchers");
            assertThat(row.getResourceId()).isEqualTo(id);
        });

        send(get("/api/crm/vouchers").param("q", voucher.get("number").asText()), CrmRole.GESTOR)
                .andExpect(jsonPath("$.content[0].provider").value("Hotel Ponta Verde"));
        send(get("/api/crm/vouchers/{id}", id), CrmRole.MARKETING).andExpect(status().isForbidden());

        send(post("/api/crm/vouchers/{id}/cancel", id), CrmRole.ATENDENTE).andExpect(jsonPath("$.status").value("CANCELLED"));
        send(put("/api/crm/vouchers/{id}", id).contentType(MediaType.APPLICATION_JSON).content(
                voucherBody("HOTEL", "{\"provider\":\"X\",\"serviceDate\":\"2027-01-29\",\"endDate\":\"2027-02-04\"}")
                        .replace("\"type\"", "\"version\":" + (voucher.get("version").asLong() + 1) + ",\"type\"")),
                CrmRole.ATENDENTE).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_CANCELLED"));
    }

    @Test
    void extractionWithoutAiProviderIs503AndSourceMustBeAVoucherUpload() throws Exception {
        send(post("/api/crm/vouchers/extract").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FLIGHT\",\"fileId\":\"" + UUID.randomUUID() + "\"}"), CrmRole.ATENDENTE)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"));

        String document = json(send(multipart("/api/crm/files").file(new MockMultipartFile("file", "doc.pdf",
                "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.ISO_8859_1))).param("category", "DOCUMENT"),
                CrmRole.ATENDENTE).andExpect(status().isCreated())).get("id").asText();
        send(post("/api/crm/vouchers").contentType(MediaType.APPLICATION_JSON).content(voucherBody("TOUR",
                "{\"description\":\"Buggy\",\"serviceDate\":\"2027-01-30\"}").replace("\"type\"",
                "\"sourceFileId\":\"" + document + "\",\"type\"")), CrmRole.ATENDENTE)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILE_CATEGORY_MISMATCH"));
    }

    // ---------- Pesquisar Voos ----------

    @Test
    void flightSearchQuotesRecordsHistoryAndRepeatingOnlyRenewsIt() throws Exception {
        when(flightSearchGateway.roundTrip(any(), any(), any(), any(), anyInt())).thenAnswer(inv -> {
            LocalDate departure = inv.getArgument(2);
            return List.of(new FlightOffer(departure, inv.getArgument(3),
                    new BigDecimal(1000 + departure.getDayOfMonth()), "BRL", List.of("LATAM"), 0, 120));
        });
        String group = json(send(post("/api/crm/flight-search/groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nordeste " + UUID.randomUUID().toString().substring(0, 4)
                        + "\",\"destinations\":[\"MCZ\",\"REC\"]}"), CrmRole.ATENDENTE)
                .andExpect(status().isCreated())).get("destinations").toString();
        assertThat(group).isEqualTo("[\"MCZ\",\"REC\"]");

        YearMonth month = YearMonth.from(BusinessTime.today(java.time.Clock.systemUTC())).plusMonths(1);
        String body = "{\"origin\":\"CXJ\",\"destinations\":[\"REC\",\"MCZ\"],\"mode\":\"FLEXIBLE\",\"months\":[\""
                + month + "\"],\"stayNights\":5}";
        send(post("/api/crm/flight-search").contentType(MediaType.APPLICATION_JSON).content(body), CrmRole.ATENDENTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinations[0].destination").value("REC"))
                .andExpect(jsonPath("$.destinations[0].offers[0].departureDate").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$.destinations[0].offers[0].returnDate").value(month.atDay(6).toString()));
        // Mesma pesquisa com destinos em outra ordem: uma linha só no histórico.
        send(post("/api/crm/flight-search").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("[\"REC\",\"MCZ\"]", "[\"MCZ\",\"REC\"]")), CrmRole.GESTOR).andExpect(status().isOk());

        JsonNode recent = json(send(get("/api/crm/flight-search/recent"), CrmRole.ATENDENTE)
                .andExpect(status().isOk()));
        assertThat(recent.size()).isLessThanOrEqualTo(5);
        assertThat(recent.findValuesAsText("summary").stream().filter(s -> s.startsWith("CXJ → MCZ, REC"))).hasSize(1);
        assertThat(recent.get(0).get("runBy").asText()).isEqualTo("gestor.crm");

        // Pesquisa não é escrita: não vira CREATE na auditoria.
        assertThat(auditLogRepository.findAll()).noneSatisfy(row -> assertThat(row.getPath())
                .isEqualTo("/api/crm/flight-search"));
        send(post("/api/crm/flight-search").contentType(MediaType.APPLICATION_JSON).content(body), CrmRole.MARKETING)
                .andExpect(status().isForbidden());
    }

    // ---------- Painel ----------

    @Test
    void dashboardShowsUpcomingFlightsOnlyToWhoCanSeeThem() throws Exception {
        createFlight("Fabio Proximo", null, LocalDateTime.now(BusinessTime.ZONE).plusDays(2));
        send(get("/api/crm/dashboard"), CrmRole.ATENDENTE).andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.next7Days").isNumber())
                .andExpect(jsonPath("$.flights.byMonth.length()").value(6))
                .andExpect(jsonPath("$.flights.upcoming[?(@.passengers[0] == 'Fabio Proximo')]").exists())
                .andExpect(jsonPath("$.vouchers.byTypeThisMonth.HOTEL").isNumber());
        send(get("/api/crm/dashboard"), CrmRole.MARKETING).andExpect(status().isOk())
                .andExpect(jsonPath("$.flights").doesNotExist())
                .andExpect(jsonPath("$.vouchers").doesNotExist());
    }

    // ---------- apoio ----------

    private JsonNode createFlight(String passenger, String second, LocalDateTime outbound) throws Exception {
        return createFlight(passenger, second, outbound, "LATAM");
    }

    private JsonNode createFlight(String passenger, String second, LocalDateTime outbound, String airline)
            throws Exception {
        return json(send(post("/api/crm/client-flights").contentType(MediaType.APPLICATION_JSON)
                .content(flightBody(passenger, second, outbound, airline).toString()), CrmRole.ATENDENTE)
                .andExpect(status().isCreated()));
    }

    private ObjectNode flightBody(String passenger, String second, LocalDateTime outbound, String airline) {
        ObjectNode body = objectMapper.createObjectNode().put("airline", airline).put("origin", "Brasília (BSB)")
                .put("destination", "Maceió (MCZ)").put("outboundAt", outbound.withNano(0).toString())
                .put("locator", "ABC123");
        body.withArray("travelers").addObject().put("fullName", passenger).put("cpf", "529.982.247-25");
        if (second != null) {
            body.withArray("travelers").addObject().put("fullName", second);
        }
        return body;
    }

    private static String voucherBody(String type, String details) {
        return "{\"type\":\"" + type + "\",\"clientName\":\"Maria Cliente\",\"clientCpf\":\"529.982.247-25\","
                + "\"clientPhone\":\"(61) 99999-0000\",\"details\":" + details + "}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, CrmRole role) throws Exception {
        return mockMvc.perform(request.with(crm(role)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
