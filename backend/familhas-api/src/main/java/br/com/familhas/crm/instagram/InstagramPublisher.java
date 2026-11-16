package br.com.familhas.crm.instagram;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.api.BusinessException;
import br.com.familhas.crm.file.CrmFileResponse;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileLinkSigner;
import br.com.familhas.crm.integration.CrmIntegration;
import br.com.familhas.crm.integration.IntegrationProperties;
import br.com.familhas.crm.integration.IntegrationRegistry;

/**
 * Publicação no Instagram (ADR 0016, D3).
 *
 * 1. Valida tudo antes (imagens JPEG, MP4 do Reel, tamanho do carrossel) e exige a Meta configurada.
 * 2. Claim atômico no banco: só um pedido passa de READY/FAILED para PUBLISHING.
 * 3. Em segundo plano: cria os contêineres (a Meta baixa as mídias por links assinados de 1 h do endereço público
 *    da API). Falha aqui = FAILED, e pode tentar de novo.
 * 4. Grava publish_attempted_at e só então chama media_publish. Qualquer falha a partir daqui = UNCERTAIN: nunca
 *    reenviar sozinho, porque a Meta pode ter publicado. Uma pessoa confere e resolve.
 */
@Service
public class InstagramPublisher {

    private static final Logger log = LoggerFactory.getLogger(InstagramPublisher.class);

    private final InstagramPostRepository repository;
    private final InstagramPostService posts;
    private final CrmFileService files;
    private final FileLinkSigner signer;
    private final MetaGateway meta;
    private final MetaProperties properties;
    private final IntegrationRegistry integrations;
    private final IntegrationProperties credentials;
    private final ObjectProvider<AuditService> audit;
    private final TransactionTemplate tx;
    private final PublishDispatcher dispatcher;
    private final Clock clock;

    public InstagramPublisher(InstagramPostRepository repository, InstagramPostService posts, CrmFileService files,
            FileLinkSigner signer, MetaGateway meta, MetaProperties properties, IntegrationRegistry integrations,
            IntegrationProperties credentials, ObjectProvider<AuditService> audit, TransactionTemplate tx,
            PublishDispatcher dispatcher, Clock clock) {
        this.repository = repository;
        this.posts = posts;
        this.files = files;
        this.signer = signer;
        this.meta = meta;
        this.properties = properties;
        this.integrations = integrations;
        this.credentials = credentials;
        this.audit = audit;
        this.tx = tx;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /** Valida, faz o claim e dispara a publicação. Volta com o post em PUBLISHING (a tela acompanha). */
    public InstagramPostResponse publish(UUID id) {
        integrations.require(CrmIntegration.INSTAGRAM);
        Plan plan = tx.execute(status -> plan(posts.find(id)));
        AuditActor actor = AuditActor.current();
        Integer claimed = tx.execute(status -> repository.claim(id, clock.instant(), actor.displayName()));
        if (claimed == null || claimed == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "PUBLISH_NOT_ALLOWED",
                    "Este post ja esta publicando, foi publicado ou aguarda conferencia.");
        }
        dispatcher.dispatch(() -> run(id, plan, actor));
        return tx.execute(status -> posts.response(posts.find(id)));
    }

    /** O que mandar para a Meta, montado e validado antes do claim. */
    Plan plan(InstagramPostEntity post) {
        if (post.getStatus() != PostStatus.READY && post.getStatus() != PostStatus.FAILED) {
            throw new BusinessException(HttpStatus.CONFLICT, "PUBLISH_NOT_ALLOWED",
                    "Marque o post como pronto (revisado) antes de publicar.");
        }
        List<CrmFileResponse> images = new ArrayList<>();
        for (InstagramPostSlideEntity slide : post.getSlides()) {
            CrmFileResponse image = files.get(slide.getImageFileId());
            if (!"image/jpeg".equals(image.contentType())) {
                throw InstagramPostService.invalid("IMAGE_NOT_JPEG",
                        "A Meta so aceita JPEG. Gere as laminas de novo para publicar.");
            }
            images.add(image);
        }
        if (post.getFormat() == PostFormat.FEED && images.size() > properties.maxCarouselItems()) {
            throw InstagramPostService.invalid("CAROUSEL_TOO_LARGE",
                    "A Meta aceita ate " + properties.maxCarouselItems() + " laminas por carrossel.");
        }
        String caption = caption(post);
        String video = post.getVideoFileId() == null ? null : link(post.getVideoFileId());
        return new Plan(post.getFormat(), images.stream().map(i -> link(i.id())).toList(), video, caption);
    }

    void run(UUID id, Plan plan, AuditActor actor) {
        String container;
        try {
            container = createContainers(plan);
        } catch (RuntimeException e) {
            String message = e instanceof MetaApiException meta && meta.tokenExpired()
                    ? "Token da Meta vencido ou invalido. Renove o META_ACCESS_TOKEN."
                    : "Falha ao preparar na Meta: " + e.getMessage();
            log.warn("Publicacao {} falhou antes do media_publish: {}", id, e.getMessage());
            finish(id, PostStatus.FAILED, message, null, null, actor);
            return;
        }
        tx.executeWithoutResult(status -> {
            InstagramPostEntity post = posts.find(id);
            post.setPublishAttemptedAt(clock.instant());
            repository.saveAndFlush(post);
        });
        try {
            String mediaId = meta.publish(container);
            finish(id, PostStatus.PUBLISHED, null, mediaId, meta.permalink(mediaId), actor);
        } catch (RuntimeException e) {
            log.error("Resultado incerto na publicacao {}: {}", id, e.getMessage());
            finish(id, PostStatus.UNCERTAIN, "Resultado incerto no media_publish (" + e.getMessage()
                    + "). Confira no Instagram antes de tentar de novo.", null, null, actor);
        }
    }

    private String createContainers(Plan plan) {
        return switch (plan.format()) {
            case STORY -> ready(meta.createContainer(params("media_type", "STORIES", "image_url", plan.images().get(0))));
            case REEL -> {
                Map<String, String> params = params("media_type", "REELS", "video_url", plan.video(), "caption",
                        plan.caption(), "share_to_feed", "true");
                if (!plan.images().isEmpty()) {
                    params.put("cover_url", plan.images().get(0));
                }
                yield ready(meta.createContainer(params));
            }
            case FEED -> {
                if (plan.images().size() == 1) {
                    yield ready(meta.createContainer(params("image_url", plan.images().get(0), "caption",
                            plan.caption())));
                }
                List<String> children = new ArrayList<>();
                for (String image : plan.images()) {
                    children.add(ready(meta.createContainer(params("image_url", image, "is_carousel_item", "true"))));
                }
                yield ready(meta.createContainer(params("media_type", "CAROUSEL", "children",
                        String.join(",", children), "caption", plan.caption())));
            }
        };
    }

    /** Espera o contêiner ficar pronto (vídeo leva tempo). ERROR, EXPIRED ou demora demais: falha antes do publish. */
    private String ready(String containerId) {
        Instant deadline = clock.instant().plus(properties.containerTimeout());
        while (true) {
            String status = meta.containerStatus(containerId);
            if ("FINISHED".equals(status) || "PUBLISHED".equals(status)) {
                return containerId;
            }
            if ("ERROR".equals(status) || "EXPIRED".equals(status) || clock.instant().isAfter(deadline)) {
                throw new MetaApiException(0, "conteiner " + containerId + " ficou " + status);
            }
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MetaApiException(0, "interrompido");
            }
        }
    }

    private void finish(UUID id, PostStatus status, String error, String mediaId, String permalink, AuditActor actor) {
        tx.executeWithoutResult(s -> {
            InstagramPostEntity post = posts.find(id);
            post.setStatus(status);
            post.setLastError(error);
            if (status == PostStatus.PUBLISHED) {
                post.setMetaMediaId(mediaId);
                post.setPermalink(permalink);
                post.setPublishedAt(clock.instant());
            }
            repository.saveAndFlush(post);
        });
        AuditService service = audit.getIfAvailable();
        if (service != null && status != PostStatus.FAILED) {
            service.record(actor, new AuditEntry(AuditAction.PUBLISH, "crm/instagram-posts", id.toString(), "POST",
                    "/api/crm/instagram-posts/" + id + "/publish", status == PostStatus.PUBLISHED ? 200 : 500,
                    status == PostStatus.PUBLISHED ? "publicado " + mediaId : "resultado incerto"));
        }
    }

    private String caption(InstagramPostEntity post) {
        String caption = post.getCaption() == null ? "" : post.getCaption();
        String hashtags = post.getHashtags() == null ? "" : post.getHashtags();
        String text = hashtags.isBlank() ? caption : caption + "\n\n" + hashtags;
        if (text.length() > 2200) {
            throw InstagramPostService.invalid("CAPTION_TOO_LONG", "Legenda com hashtags passa de 2.200 caracteres.");
        }
        return text;
    }

    /** Link assinado (1 h) no endereço público da API, para a Meta baixar a mídia. */
    private String link(UUID fileId) {
        long expires = clock.instant().plus(properties.mediaLinkTtl()).getEpochSecond();
        String base = credentials.metaPublicBaseUrl().replaceAll("/+$", "");
        return base + CrmFileService.CONTENT_PATH.formatted(fileId, expires, signer.sign(fileId, expires));
    }

    private static Map<String, String> params(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    record Plan(PostFormat format, List<String> images, String video, String caption) {
    }
}
