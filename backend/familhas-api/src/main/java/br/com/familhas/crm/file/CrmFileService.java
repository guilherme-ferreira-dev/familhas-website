package br.com.familhas.crm.file;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.PageResponse;
import br.com.familhas.crm.CrmAccess;
import br.com.familhas.crm.CrmAction;

/**
 * Arquivos privados do CRM (ADR 0014). A permissão vem da categoria; o tipo, dos primeiros bytes; o nome no disco,
 * de um UUID. Download só por link assinado de curta duração, e cada link emitido vai para a auditoria.
 */
@Service
public class CrmFileService {

    private static final Logger log = LoggerFactory.getLogger(CrmFileService.class);
    public static final String CONTENT_PATH = "/api/crm/files/%s/content?expires=%d&signature=%s";

    private final CrmFileRepository repository;
    private final FileStorage storage;
    private final FileLinkSigner signer;
    private final StorageProperties properties;
    private final ObjectProvider<AuditService> auditService;
    private final Clock clock;

    public CrmFileService(CrmFileRepository repository, FileStorage storage, FileLinkSigner signer,
            StorageProperties properties, ObjectProvider<AuditService> auditService, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.signer = signer;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CrmFileResponse upload(FileCategory category, MultipartFile file) {
        CrmAccess.require(category.resource(), CrmAction.CREATE);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "Arquivo vazio.");
        }
        try (InputStream raw = file.getInputStream()) {
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(StoredFileType.HEADER_BYTES);
            byte[] header = in.readNBytes(StoredFileType.HEADER_BYTES);
            in.reset();
            StoredFileType type = checkType(category, file.getContentType(), header);
            if (file.getSize() > type.maxBytes()) {
                throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                        "Arquivo maior que o permitido para " + type.extension().toUpperCase() + ".");
            }

            String key = UUID.randomUUID().toString();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            LimitedInputStream limited = new LimitedInputStream(new DigestInputStream(in, sha256), type.maxBytes());
            storage.put(key, limited);
            deleteFromStorageOnRollback(key);

            AuditActor actor = AuditActor.current();
            CrmFileEntity entity = new CrmFileEntity();
            entity.setCategory(category);
            entity.setStorageKey(key);
            entity.setOriginalName(safeName(file.getOriginalFilename(), type));
            entity.setContentType(type.contentType());
            entity.setSizeBytes(limited.count());
            entity.setSha256(HexFormat.of().formatHex(sha256.digest()));
            entity.setUploadedBy(actor.subject());
            entity.setUploadedByName(actor.username());
            return CrmFileResponse.of(repository.save(entity));
        } catch (LimitExceededException e) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Arquivo maior que o permitido.");
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar o arquivo no storage", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public CrmFileResponse get(UUID id) {
        CrmFileEntity entity = find(id);
        CrmAccess.require(entity.getCategory().resource(), CrmAction.READ);
        return CrmFileResponse.of(entity);
    }

    /**
     * Metadados de arquivos já ligados a um registro (anexos de voo, origem de voucher). Quem chama já conferiu a
     * permissão do registro; ids de outra categoria são ignorados.
     */
    @Transactional(readOnly = true)
    public List<CrmFileResponse> findAttached(FileCategory category, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids).stream().filter(f -> f.getCategory() == category)
                .sorted(java.util.Comparator.comparing(CrmFileEntity::getCreatedAt))
                .map(CrmFileResponse::of).toList();
    }

    /** Conteúdo para processamento no servidor (ex.: extração). Exige ler a categoria do arquivo. */
    @Transactional(readOnly = true)
    public byte[] readContent(UUID id) {
        CrmFileEntity entity = find(id);
        CrmAccess.require(entity.getCategory().resource(), CrmAction.READ);
        try (InputStream in = storage.open(entity.getStorageKey())) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Arquivo sem conteudo no storage: " + id, e);
        }
    }

    /** Categoria do arquivo, para o módulo conferir se o id enviado é mesmo do tipo esperado. */
    @Transactional(readOnly = true)
    public FileCategory categoryOf(UUID id) {
        return find(id).getCategory();
    }

    @Transactional(readOnly = true)
    public PageResponse<CrmFileResponse> search(FileCategory category, String name, Pageable pageable) {
        CrmAccess.require(category.resource(), CrmAction.READ);
        String normalized = name == null || name.isBlank() ? null : name.trim().toLowerCase();
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(repository.search(category, normalized, sorted), CrmFileResponse::of);
    }

    @Transactional
    public void delete(UUID id) {
        CrmFileEntity entity = find(id);
        CrmAccess.require(entity.getCategory().resource(), CrmAction.DELETE);
        repository.delete(entity);
        String key = entity.getStorageKey();
        afterCommit(() -> {
            try {
                storage.delete(key);
            } catch (IOException e) {
                log.error("Arquivo apagado do banco, mas nao do storage: key={}", key, e);
            }
        });
    }

    /** Link de download assinado. Registrado na auditoria (DOWNLOAD), com a categoria; menos a logo oficial. */
    @Transactional(readOnly = true)
    public FileLinkResponse createLink(UUID id) {
        CrmFileEntity entity = find(id);
        CrmAccess.require(entity.getCategory().resource(), CrmAction.READ);
        Instant expiresAt = clock.instant().plus(properties.linkTtl()).truncatedTo(ChronoUnit.SECONDS);
        long expires = expiresAt.getEpochSecond();
        AuditService audit = auditService.getIfAvailable();
        // A logo oficial não é dado sensível e é lida a cada sessão: fica fora da auditoria de downloads.
        if (audit != null && entity.getCategory() != FileCategory.BRAND_ASSET) {
            audit.record(AuditActor.current(), new AuditEntry(AuditAction.DOWNLOAD, "crm/files", id.toString(), "POST",
                    "/api/crm/files/" + id + "/link", 200, "categoria=" + entity.getCategory()));
        }
        return new FileLinkResponse(CONTENT_PATH.formatted(id, expires, signer.sign(id, expires)), expiresAt);
    }

    /** Conteúdo por link assinado. Link inválido, vencido ou de arquivo apagado: o mesmo 403, sem dizer qual. */
    @Transactional(readOnly = true)
    public StoredContent openSigned(UUID id, long expires, String signature) {
        if (!signer.isValid(id, expires, signature)) {
            throw linkInvalid();
        }
        CrmFileEntity entity = repository.findById(id).orElseThrow(CrmFileService::linkInvalid);
        try {
            return new StoredContent(entity.getOriginalName(), entity.getContentType(), entity.getSizeBytes(),
                    storage.open(entity.getStorageKey()));
        } catch (IOException e) {
            log.error("Arquivo sem conteudo no storage: id={}", id, e);
            throw linkInvalid();
        }
    }

    public record StoredContent(String name, String contentType, long sizeBytes, InputStream stream) {
    }

    static StoredFileType checkType(FileCategory category, String declaredContentType, byte[] header) {
        StoredFileType type = StoredFileType.detect(header, header.length).orElseThrow(() -> new BusinessException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE_NOT_ALLOWED", "Tipo de arquivo nao aceito."));
        if (!category.allowedTypes().contains(type)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE_NOT_ALLOWED",
                    "Tipo de arquivo nao aceito aqui.");
        }
        // O navegador declarou outro tipo aceito (ex.: .png que na verdade é PDF): recusa em vez de adivinhar.
        if (declaredContentType != null) {
            StoredFileType.fromContentType(declaredContentType.toLowerCase()).filter(declared -> declared != type)
                    .ifPresent(declared -> {
                        throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE_MISMATCH",
                                "O conteudo do arquivo nao corresponde ao tipo informado.");
                    });
        }
        return type;
    }

    /** Só para exibição: sem pasta, sem caracteres de controle, até 200 caracteres, com a extensão do tipo real. */
    static String safeName(String original, StoredFileType type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|*?:]", "").strip();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (base.isBlank()) {
            base = "arquivo";
        }
        String suffix = "." + type.extension();
        return (base.length() + suffix.length() > 200 ? base.substring(0, 200 - suffix.length()) : base) + suffix;
    }

    private CrmFileEntity find(UUID id) {
        return repository.findById(id).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "Arquivo nao encontrado."));
    }

    private static BusinessException linkInvalid() {
        return new BusinessException(HttpStatus.FORBIDDEN, "FILE_LINK_INVALID", "Link invalido ou vencido.");
    }

    private void deleteFromStorageOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        storage.delete(key);
                    } catch (IOException e) {
                        log.warn("Arquivo orfao no storage depois de rollback: key={}", key, e);
                    }
                }
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    static final class LimitExceededException extends IOException {
        LimitExceededException() {
            super("limite de tamanho excedido");
        }
    }

    /** Conta os bytes e corta no limite, mesmo que o tamanho declarado no multipart esteja errado. */
    static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        long count() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                add(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        private void add(long n) throws LimitExceededException {
            count += n;
            if (count > limit) {
                throw new LimitExceededException();
            }
        }
    }
}
