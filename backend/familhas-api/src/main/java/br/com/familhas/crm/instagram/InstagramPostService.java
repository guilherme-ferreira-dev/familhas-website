package br.com.familhas.crm.instagram;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.PageResponse;
import br.com.familhas.crm.file.CrmFileResponse;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileCategory;

/**
 * Fila de posts do Instagram (ADR 0016). Formatos: STORY (1 imagem 9:16), FEED (1 imagem ou carrossel de até 20
 * lâminas 1:1) e REEL (MP4 vertical, usado como veio). Editar volta o post para rascunho: publicar exige que alguém
 * marque "pronto" depois de revisar (revisão humana antes de publicar ofertas).
 */
@Service
public class InstagramPostService {

    static final Set<PostStatus> EDITABLE = EnumSet.of(PostStatus.DRAFT, PostStatus.READY, PostStatus.FAILED);
    static final int MAX_HASHTAGS = 30;

    private final InstagramPostRepository repository;
    private final CrmFileService files;

    public InstagramPostService(InstagramPostRepository repository, CrmFileService files) {
        this.repository = repository;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public PageResponse<InstagramPostSummary> list(Set<PostStatus> statuses, Pageable pageable) {
        Set<PostStatus> filter = statuses == null || statuses.isEmpty() ? EnumSet.allOf(PostStatus.class) : statuses;
        return PageResponse.of(repository.findByStatusIn(filter, PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))),
                p -> new InstagramPostSummary(p.getId(), p.getFormat(), p.getOrigin(), p.getStatus(), p.getTitle(),
                        p.getSlides().size(), p.getVideoFileId() != null, p.getPermalink(), p.getCreatedBy(),
                        p.getCreatedAt(), p.getPublishedAt()));
    }

    @Transactional(readOnly = true)
    public InstagramPostResponse get(UUID id) {
        return response(find(id));
    }

    @Transactional
    public InstagramPostResponse create(InstagramPostRequest request) {
        InstagramPostEntity post = new InstagramPostEntity();
        post.setOrigin(request.origin() == null ? PostOrigin.MANUAL : request.origin());
        post.setCreatedBy(AuditActor.current().displayName());
        post.setStatus(PostStatus.DRAFT);
        apply(post, request);
        return response(repository.saveAndFlush(post));
    }

    @Transactional
    public InstagramPostResponse update(UUID id, InstagramPostRequest request) {
        InstagramPostEntity post = find(id);
        if (request.version() == null || request.version() != post.getVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Post alterado por outra pessoa. Recarregue antes de salvar.");
        }
        requireEditable(post);
        Set<UUID> before = fileIds(post);
        post.setStatus(PostStatus.DRAFT);
        post.setLastError(null);
        apply(post, request);
        repository.saveAndFlush(post);
        Set<UUID> removed = new HashSet<>(before);
        removed.removeAll(fileIds(post));
        removed.forEach(files::delete);
        return response(post);
    }

    /** Revisado por uma pessoa: pode publicar. Confere o que a Meta vai exigir. */
    @Transactional
    public InstagramPostResponse markReady(UUID id) {
        InstagramPostEntity post = find(id);
        requireEditable(post);
        if ((post.getFormat() == PostFormat.FEED || post.getFormat() == PostFormat.REEL)
                && (post.getCaption() == null || post.getCaption().isBlank())) {
            throw invalid("CAPTION_REQUIRED", "Escreva a legenda antes de marcar como pronto.");
        }
        post.setStatus(PostStatus.READY);
        return response(repository.saveAndFlush(post));
    }

    /**
     * Depois de um resultado incerto, alguém confere no Instagram. Publicado: fecha o post. Não publicado: volta para
     * pronto e pode ser publicado de novo. Nunca há reenvio automático.
     */
    @Transactional
    public InstagramPostResponse resolveUncertain(UUID id, boolean published, String permalink) {
        InstagramPostEntity post = find(id);
        if (post.getStatus() != PostStatus.UNCERTAIN) {
            throw new BusinessException(HttpStatus.CONFLICT, "POST_NOT_UNCERTAIN", "Este post nao esta em conferencia.");
        }
        if (published) {
            post.setStatus(PostStatus.PUBLISHED);
            post.setPublishedAt(post.getPublishAttemptedAt());
            post.setPermalink(permalink == null || permalink.isBlank() ? null : permalink.strip());
            post.setLastError(null);
        } else {
            post.setStatus(PostStatus.READY);
            post.setPublishAttemptedAt(null);
            post.setPublishClaimedAt(null);
            post.setLastError("Conferido por " + AuditActor.current().displayName() + ": nao foi publicado.");
        }
        return response(repository.saveAndFlush(post));
    }

    @Transactional
    public void delete(UUID id) {
        InstagramPostEntity post = find(id);
        requireEditable(post);
        Set<UUID> owned = fileIds(post);
        repository.delete(post);
        repository.flush();
        owned.forEach(files::delete);
    }

    InstagramPostEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "POST_NOT_FOUND", "Post nao encontrado."));
    }

    InstagramPostResponse response(InstagramPostEntity p) {
        Map<UUID, CrmFileResponse> media = files.findAttached(FileCategory.INSTAGRAM_MEDIA, fileIds(p)).stream()
                .collect(Collectors.toMap(CrmFileResponse::id, Function.identity()));
        List<InstagramPostResponse.Slide> slides = p.getSlides().stream().map(s -> new InstagramPostResponse.Slide(
                s.getSortOrder(), media.get(s.getImageFileId()),
                s.getCleanFileId() == null ? null : media.get(s.getCleanFileId()), s.getHook(), s.getTitle(),
                s.getBullets() == null ? List.of() : Arrays.asList(s.getBullets().split("\n")), s.getCaption()))
                .toList();
        return new InstagramPostResponse(p.getId(), p.getFormat(), p.getOrigin(), p.getStatus(), p.getTitle(),
                p.getIdea(), p.getCaption(), p.getHashtags(), slides,
                p.getVideoFileId() == null ? null : media.get(p.getVideoFileId()), p.getMetaMediaId(),
                p.getPermalink(), p.getLastError(), p.getPublishAttemptedAt(), p.getPublishedAt(), p.getPublishedBy(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }

    private void apply(InstagramPostEntity post, InstagramPostRequest request) {
        List<InstagramPostRequest.Slide> slides = request.slides() == null ? List.of() : request.slides();
        switch (request.format()) {
            case STORY -> {
                if (slides.size() != 1 || request.videoFileId() != null) {
                    throw invalid("INVALID_POST_SHAPE", "Story leva exatamente uma imagem 9:16.");
                }
            }
            case FEED -> {
                if (slides.isEmpty() || request.videoFileId() != null) {
                    throw invalid("INVALID_POST_SHAPE", "Feed leva de 1 a 20 imagens 1:1 (2 ou mais = carrossel).");
                }
            }
            case REEL -> {
                if (request.videoFileId() == null || slides.size() > 1) {
                    throw invalid("INVALID_POST_SHAPE", "Reel leva um video MP4 vertical e, opcionalmente, a capa.");
                }
            }
        }
        if (hashtagCount(request.hashtags()) > MAX_HASHTAGS) {
            throw invalid("TOO_MANY_HASHTAGS", "O Instagram aceita no maximo 30 hashtags.");
        }
        Set<UUID> ids = new HashSet<>();
        for (InstagramPostRequest.Slide slide : slides) {
            requireMedia(slide.imageFileId(), false);
            if (slide.cleanFileId() != null) {
                requireMedia(slide.cleanFileId(), false);
            }
            if (!ids.add(slide.imageFileId())) {
                throw invalid("DUPLICATED_MEDIA", "A mesma imagem aparece em duas laminas.");
            }
        }
        if (request.videoFileId() != null) {
            requireMedia(request.videoFileId(), true);
        }
        post.setFormat(request.format());
        post.setTitle(request.title().strip());
        post.setIdea(blankToNull(request.idea()));
        post.setCaption(blankToNull(request.caption()));
        post.setHashtags(blankToNull(request.hashtags()));
        post.setVideoFileId(request.videoFileId());

        post.getSlides().clear();
        int order = 0;
        for (InstagramPostRequest.Slide input : slides) {
            InstagramPostSlideEntity slide = new InstagramPostSlideEntity();
            slide.setPost(post);
            slide.setSortOrder(order++);
            slide.setImageFileId(input.imageFileId());
            slide.setCleanFileId(input.cleanFileId());
            slide.setHook(blankToNull(input.hook()));
            slide.setTitle(blankToNull(input.title()));
            slide.setBullets(input.bullets() == null || input.bullets().isEmpty() ? null
                    : input.bullets().stream().map(String::strip).filter(b -> !b.isEmpty())
                            .collect(Collectors.joining("\n")));
            slide.setCaption(blankToNull(input.caption()));
            post.getSlides().add(slide);
        }
    }

    private void requireMedia(UUID fileId, boolean video) {
        if (files.categoryOf(fileId) != FileCategory.INSTAGRAM_MEDIA) {
            throw invalid("FILE_CATEGORY_MISMATCH", "Use arquivos enviados para o Instagram.");
        }
        boolean isVideo = "video/mp4".equals(files.get(fileId).contentType());
        if (isVideo != video) {
            throw invalid("INVALID_POST_SHAPE", video ? "O Reel precisa de um video MP4." : "Lamina precisa ser imagem.");
        }
    }

    static int hashtagCount(String hashtags) {
        if (hashtags == null) {
            return 0;
        }
        return (int) Arrays.stream(hashtags.split("\\s+")).filter(t -> t.startsWith("#") && t.length() > 1).count();
    }

    static Set<UUID> fileIds(InstagramPostEntity post) {
        Set<UUID> ids = new HashSet<>();
        post.getSlides().forEach(s -> {
            ids.add(s.getImageFileId());
            if (s.getCleanFileId() != null) {
                ids.add(s.getCleanFileId());
            }
        });
        if (post.getVideoFileId() != null) {
            ids.add(post.getVideoFileId());
        }
        return ids;
    }

    private static void requireEditable(InstagramPostEntity post) {
        if (!EDITABLE.contains(post.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "POST_LOCKED",
                    "Post publicado, publicando ou em conferencia nao pode ser alterado.");
        }
    }

    static BusinessException invalid(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
