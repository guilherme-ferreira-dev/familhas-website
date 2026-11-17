package br.com.familhas.crm;

import static br.com.familhas.crm.CrmTestTokens.crm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditLogRepository;
import br.com.familhas.crm.instagram.InstagramPostRepository;
import br.com.familhas.crm.instagram.InstagramPublishRecovery;
import br.com.familhas.crm.instagram.MetaApiException;
import br.com.familhas.crm.instagram.MetaGateway;
import br.com.familhas.crm.instagram.PostStatus;
import br.com.familhas.crm.marketing.ImageGenerator;

/**
 * Marketing com Oracle real (prompt mestre, Etapa 4; ADR 0016): V22, logo oficial, fila do Instagram com
 * publicação pela Meta (dublê do gateway, publicação síncrona), roteiros, IA de imagem (dublê da OpenAI, ADR 0019)
 * e IA de texto e vídeo não configuradas.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.admin.payment-feed.enabled=false",
        "familhas.integrations.meta-access-token=token-de-teste",
        "familhas.integrations.meta-instagram-user-id=17841400000",
        "familhas.integrations.meta-public-base-url=https://crm.exemplo.com.br",
        "familhas.meta.publish-async=false",
        "familhas.meta.recovery-delay=PT10H",
        "familhas.integrations.ai-image-provider=openai",
        "familhas.integrations.openai-api-key=sk-teste",
        "familhas.openai.images-per-hour=3",
        "familhas.storage.local-root=${java.io.tmpdir}/familhas-crm-it4" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrmMarketingIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1 };
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
    static final byte[] MP4 = "\u0000\u0000\u0000\u0018ftypmp42isom".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private InstagramPostRepository posts;
    @Autowired private InstagramPublishRecovery recovery;
    @Autowired private TransactionTemplate tx;
    @MockitoBean private MetaGateway meta;
    @MockitoBean private ImageGenerator images;

    @BeforeEach
    void metaWorks() {
        reset(meta);
        when(meta.createContainer(anyMap())).thenAnswer(inv -> "c-" + UUID.randomUUID().toString().substring(0, 8));
        when(meta.containerStatus(anyString())).thenReturn("FINISHED");
        when(meta.publish(anyString())).thenReturn("m-1");
        when(meta.permalink("m-1")).thenReturn("https://www.instagram.com/p/abc/");
    }

    // ---------- Logo ----------

    @Test
    void officialLogoIsSetByManagersAndReadByTheTeam() throws Exception {
        send(multipart("/api/crm/brand/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                .with(r -> { r.setMethod("PUT"); return r; }), CrmRole.MARKETING).andExpect(status().isForbidden());
        send(multipart("/api/crm/brand/logo").file(new MockMultipartFile("file", "logo.jpg", "image/jpeg", JPEG))
                .with(r -> { r.setMethod("PUT"); return r; }), CrmRole.GESTOR)
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
        String first = json(send(multipart("/api/crm/brand/logo").file(new MockMultipartFile("file", "logo.png",
                "image/png", PNG)).with(r -> { r.setMethod("PUT"); return r; }), CrmRole.GESTOR)
                .andExpect(status().isOk())).at("/logo/id").asText();
        send(multipart("/api/crm/brand/logo").file(new MockMultipartFile("file", "logo2.png", "image/png", PNG))
                .with(r -> { r.setMethod("PUT"); return r; }), CrmRole.ADMIN).andExpect(status().isOk());
        send(get("/api/crm/brand"), CrmRole.ATENDENTE).andExpect(status().isOk())
                .andExpect(jsonPath("$.logo.name").value("logo2.png"));
        send(get("/api/crm/files/{id}", first), CrmRole.ADMIN).andExpect(status().isNotFound());
    }

    // ---------- Fila do Instagram ----------

    @Test
    void carouselIsReviewedThenPublishedOnceAndAudited() throws Exception {
        String id = createPost("FEED", List.of(upload(JPEG, "1.jpg"), upload(JPEG, "2.jpg")), null, "Legenda");

        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLISH_NOT_ALLOWED"));
        send(post("/api/crm/instagram-posts/{id}/ready", id), CrmRole.MARKETING).andExpect(jsonPath("$.status").value("READY"));
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING).andExpect(status().isAccepted());

        send(get("/api/crm/instagram-posts/{id}", id), CrmRole.MARKETING)
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.permalink").value("https://www.instagram.com/p/abc/"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(meta, times(3)).createContainer(params.capture());
        assertThat(params.getAllValues().get(0)).containsEntry("is_carousel_item", "true")
                .hasEntrySatisfying("image_url", url -> assertThat(url)
                        .startsWith("https://crm.exemplo.com.br/api/crm/files/").contains("signature="));
        assertThat(params.getAllValues().get(2)).containsEntry("media_type", "CAROUSEL")
                .containsEntry("caption", "Legenda\n\n#voefamilhas #maceio");
        verify(meta, times(1)).publish(anyString());

        // Publicado: não publica de novo nem edita.
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING).andExpect(status().isConflict());
        send(delete("/api/crm/instagram-posts/{id}", id), CrmRole.MARKETING).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_LOCKED"));
        assertThat(auditLogRepository.findAll()).anySatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.PUBLISH);
            assertThat(row.getResourceId()).isEqualTo(id);
            assertThat(row.getStatus()).isEqualTo(200);
        });
    }

    @Test
    void failureBeforeMediaPublishCanBeRetriedButAfterItIsUncertain() throws Exception {
        String id = readyPost();
        when(meta.createContainer(anyMap())).thenThrow(new MetaApiException(190, "Error validating access token"));
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING).andExpect(status().isAccepted());
        send(get("/api/crm/instagram-posts/{id}", id), CrmRole.MARKETING)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.lastError").value(org.hamcrest.Matchers.containsString("Token da Meta")));
        verify(meta, never()).publish(anyString());

        // Tenta de novo; agora o media_publish estoura: resultado incerto, sem reenvio.
        reset(meta);
        when(meta.createContainer(anyMap())).thenReturn("c-1");
        when(meta.containerStatus("c-1")).thenReturn("FINISHED");
        when(meta.publish("c-1")).thenThrow(new RuntimeException("timeout"));
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING).andExpect(status().isAccepted());
        send(get("/api/crm/instagram-posts/{id}", id), CrmRole.MARKETING)
                .andExpect(jsonPath("$.status").value("UNCERTAIN"))
                .andExpect(jsonPath("$.publishAttemptedAt").isNotEmpty());
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING).andExpect(status().isConflict());
        verify(meta, times(1)).publish(anyString());

        // Alguém conferiu: não saiu. Volta para pronto e publica.
        send(post("/api/crm/instagram-posts/{id}/resolve", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":false}"), CrmRole.MARKETING).andExpect(jsonPath("$.status").value("READY"));
        doReturn("m-1").when(meta).publish("c-1");
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING).andExpect(status().isAccepted());
        send(get("/api/crm/instagram-posts/{id}", id), CrmRole.MARKETING).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void onlyOneConcurrentClaimWins() throws Exception {
        UUID id = UUID.fromString(readyPost());
        List<Callable<Integer>> claims = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            claims.add(() -> tx.execute(s -> posts.claim(id, Instant.now(), "teste")));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        int won = 0;
        for (Future<Integer> result : pool.invokeAll(claims)) {
            won += result.get();
        }
        pool.shutdown();
        assertThat(won).isEqualTo(1);
    }

    @Test
    void stuckPublicationsAreResolvedWithoutResending() throws Exception {
        UUID notAttempted = UUID.fromString(readyPost());
        UUID attempted = UUID.fromString(readyPost());
        Instant old = Instant.now().minus(Duration.ofHours(2));
        tx.executeWithoutResult(s -> {
            posts.claim(notAttempted, old, "teste");
            posts.claim(attempted, old, "teste");
        });
        tx.executeWithoutResult(s -> posts.findById(attempted).orElseThrow().setPublishAttemptedAt(old));

        assertThat(recovery.recover()).isGreaterThanOrEqualTo(2);
        assertThat(posts.findById(notAttempted).orElseThrow().getStatus()).isEqualTo(PostStatus.FAILED);
        assertThat(posts.findById(attempted).orElseThrow().getStatus()).isEqualTo(PostStatus.UNCERTAIN);
    }

    @Test
    void shapeAndMediaRulesBeforeAnythingReachesMeta() throws Exception {
        String png = upload(PNG, "slide.png");
        String id = createPost("STORY", List.of(png), null, null);
        send(post("/api/crm/instagram-posts/{id}/ready", id), CrmRole.MARKETING).andExpect(status().isOk());
        send(post("/api/crm/instagram-posts/{id}/publish", id), CrmRole.MARKETING)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IMAGE_NOT_JPEG"));

        String video = upload(MP4, "reel.mp4");
        send(post("/api/crm/instagram-posts").contentType(MediaType.APPLICATION_JSON).content(body("REEL", List.of(), null, "x")),
                CrmRole.MARKETING).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_POST_SHAPE"));
        String reel = createPost("REEL", List.of(), video, null);
        send(post("/api/crm/instagram-posts/{id}/ready", reel), CrmRole.MARKETING)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAPTION_REQUIRED"));
        send(post("/api/crm/instagram-posts").contentType(MediaType.APPLICATION_JSON)
                .content(body("FEED", List.of(video), null, "x")), CrmRole.MARKETING)
                .andExpect(status().isUnprocessableEntity());

        // Excluir o post apaga as mídias.
        send(delete("/api/crm/instagram-posts/{id}", reel), CrmRole.MARKETING).andExpect(status().isNoContent());
        send(get("/api/crm/files/{id}", video), CrmRole.MARKETING).andExpect(status().isNotFound());
        send(post("/api/crm/instagram-posts").contentType(MediaType.APPLICATION_JSON).content(body("FEED", List.of(png),
                null, "x")), CrmRole.ATENDENTE).andExpect(status().isForbidden());
    }

    // ---------- IA não configurada ----------

    @Test
    void aiEndpointsValidateFirstThenAnswer503() throws Exception {
        send(post("/api/crm/instagram-posts/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"format\":\"FEED\",\"idea\":\"Dia dos namorados em Maceió\",\"slides\":5}"), CrmRole.MARKETING)
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"));
        send(post("/api/crm/instagram-posts/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"format\":\"FEED\",\"slides\":21}"), CrmRole.MARKETING).andExpect(status().isBadRequest());
        send(post("/api/crm/instagram-posts/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"format\":\"FEED\",\"inspirationUrl\":\"https://concorrente.com.br/post\"}"), CrmRole.MARKETING)
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/crm/marketing/extract-quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CRUISE\",\"url\":\"https://169.254.169.254/latest\"}"), CrmRole.MARKETING)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("URL_NOT_ALLOWED"));
        send(post("/api/crm/itineraries/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"Maceió\",\"days\":5}"), CrmRole.ATENDENTE).andExpect(status().isServiceUnavailable());
    }

    // ---------- IA de imagem (OpenAI, ADR 0019) ----------

    @Test
    void generatedImageBecomesAPostImageFileOfTheCrm() throws Exception {
        when(images.generate(anyString())).thenReturn(JPEG);

        JsonNode file = json(send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"CITY\",\"name\":\"Maceió\"}"), CrmRole.MARKETING)
                .andExpect(status().isOk()));
        assertThat(file.get("category").asText()).isEqualTo("POST_IMAGE");
        assertThat(file.get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(file.get("name").asText()).isEqualTo("ia-Maceió.jpg");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(images).generate(prompt.capture());
        assertThat(prompt.getValue()).contains("Maceió").contains("no text");

        String id = file.get("id").asText();
        send(post("/api/crm/files/{id}/link", id), CrmRole.MARKETING).andExpect(status().isOk());
        assertThat(auditLogRepository.findAll()).anySatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(row.getResource()).isEqualTo("crm/marketing");
            assertThat(row.getStatus()).isEqualTo(200);
        });

        // Atendimento não cria posts: barrado antes de gastar com a OpenAI.
        reset(images);
        send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"SHIP\",\"name\":\"MSC Seaview\"}"), CrmRole.ATENDENTE)
                .andExpect(status().isForbidden());
        send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"SHIP\",\"name\":\"\\\"\\\"\"}"), CrmRole.MARKETING)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NAME_REQUIRED"));
        verify(images, never()).generate(anyString());
    }

    @Test
    void imageRefusalAndHourlyQuotaReachTheScreen() throws Exception {
        when(images.generate(anyString())).thenThrow(new br.com.familhas.api.BusinessException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "AI_IMAGE_REFUSED", "Recusada."));
        String ship = "{\"subject\":\"SHIP\",\"name\":\"MSC Seaview\"}";
        send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON).content(ship),
                CrmRole.GESTOR).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_IMAGE_REFUSED"));
        send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON).content(ship),
                CrmRole.GESTOR).andExpect(status().isUnprocessableEntity());
        send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON).content(ship),
                CrmRole.GESTOR).andExpect(status().isUnprocessableEntity());
        // Teto de 3 por hora neste teste: a quarta nem chama a OpenAI.
        send(post("/api/crm/marketing/generate-image").contentType(MediaType.APPLICATION_JSON).content(ship),
                CrmRole.GESTOR).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_IMAGE_QUOTA"));
        verify(images, times(3)).generate(anyString());
    }

    // ---------- Roteiros ----------

    @Test
    void itineraryKeepsInternalAlertsAndFlagsTheMasterModel() throws Exception {
        String body = """
                {"destination":"Maceió","days":3,"clientName":"Ana","clientPhone":"(61) 98888-7777",
                 "travelPeriod":"29 de Jan a 01 de Fev","priceText":"R$ 4.980 para 2 pessoas",
                 "paymentTerms":"PIX ou cartão em 10x sem juros","observations":"Incluir passeio de buggy",
                 "internalAlerts":"Cliente pediu desconto; nao prometer",
                 "sections":{"overview":"Mar verde e piscinas naturais.",
                   "days":[{"day":1,"title":"Chegada","description":"Voo LATAM às 08:30."}],
                   "included":["Hospedagem com café"],"investmentNotes":null}}""";
        JsonNode created = json(send(post("/api/crm/itineraries").contentType(MediaType.APPLICATION_JSON).content(body),
                CrmRole.ATENDENTE).andExpect(status().isCreated()));
        assertThat(created.get("warnings").toString()).contains("08:30").contains("LATAM");
        assertThat(created.get("internalAlerts").asText()).contains("nao prometer");

        send(put("/api/crm/itineraries/{id}", created.get("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"days\":3", "\"days\":3,\"version\":" + created.get("version").asLong())
                        .replace("{\"day\":1", "{\"day\":4")), CrmRole.ATENDENTE)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DAY_OUT_OF_RANGE"));
        send(get("/api/crm/itineraries").param("q", "maceio"), CrmRole.MARKETING).andExpect(status().isOk());
        send(post("/api/crm/itineraries").contentType(MediaType.APPLICATION_JSON).content(body), CrmRole.MARKETING)
                .andExpect(status().isForbidden());
    }

    // ---------- apoio ----------

    private String readyPost() throws Exception {
        String id = createPost("FEED", List.of(upload(JPEG, "a.jpg")), null, "Legenda");
        send(post("/api/crm/instagram-posts/{id}/ready", id), CrmRole.MARKETING).andExpect(status().isOk());
        return id;
    }

    private String createPost(String format, List<String> images, String video, String caption) throws Exception {
        return json(send(post("/api/crm/instagram-posts").contentType(MediaType.APPLICATION_JSON)
                .content(body(format, images, video, caption)), CrmRole.MARKETING).andExpect(status().isCreated()))
                .get("id").asText();
    }

    private String body(String format, List<String> images, String video, String caption) throws Exception {
        var node = objectMapper.createObjectNode().put("format", format).put("title", "Maceió em fevereiro")
                .put("caption", caption).put("hashtags", "#voefamilhas #maceio");
        if (video != null) {
            node.put("videoFileId", video);
        }
        var slides = node.putArray("slides");
        for (String image : images) {
            slides.addObject().put("imageFileId", image).put("title", "Praia do Gunga").putArray("bullets")
                    .add("Piscinas naturais").add("Mar verde").add("Passeio de buggy");
        }
        return node.toString();
    }

    private String upload(byte[] content, String name) throws Exception {
        String type = name.endsWith(".jpg") ? "image/jpeg" : name.endsWith(".png") ? "image/png" : "video/mp4";
        return json(send(multipart("/api/crm/files").file(new MockMultipartFile("file", name, type, content))
                .param("category", "INSTAGRAM_MEDIA"), CrmRole.MARKETING).andExpect(status().isCreated()))
                .get("id").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, CrmRole role) throws Exception {
        return mockMvc.perform(request.with(crm(role)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
