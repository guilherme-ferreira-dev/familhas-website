package br.com.familhas.crm.file;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos aceitos no storage do CRM. O tipo vem da assinatura dos primeiros bytes (magic number), nunca só da
 * extensão ou do Content-Type enviado pelo navegador (ADR 0014).
 */
public enum StoredFileType {
    PDF("application/pdf", "pdf", 15L * 1024 * 1024),
    JPEG("image/jpeg", "jpg", 10L * 1024 * 1024),
    PNG("image/png", "png", 10L * 1024 * 1024),
    WEBP("image/webp", "webp", 10L * 1024 * 1024),
    MP4("video/mp4", "mp4", 100L * 1024 * 1024);

    /** Bytes lidos do início do arquivo para detectar o tipo. */
    public static final int HEADER_BYTES = 16;

    private final String contentType;
    private final String extension;
    private final long maxBytes;

    StoredFileType(String contentType, String extension, long maxBytes) {
        this.contentType = contentType;
        this.extension = extension;
        this.maxBytes = maxBytes;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public static Optional<StoredFileType> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
    }

    /** Tipo pelos primeiros bytes; vazio quando não é nenhum dos aceitos. */
    public static Optional<StoredFileType> detect(byte[] header, int length) {
        if (startsWith(header, length, 0, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(PDF);
        }
        if (startsWith(header, length, 0, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, length, 0,
                new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A })) {
            return Optional.of(PNG);
        }
        if (startsWith(header, length, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && startsWith(header, length, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(WEBP);
        }
        if (startsWith(header, length, 4, "ftyp".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(MP4);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int length, int offset, byte[] prefix) {
        if (length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
