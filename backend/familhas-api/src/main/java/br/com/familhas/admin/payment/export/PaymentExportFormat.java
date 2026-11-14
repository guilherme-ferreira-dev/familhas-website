package br.com.familhas.admin.payment.export;

import org.springframework.http.MediaType;

public enum PaymentExportFormat {
    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    CSV("csv", MediaType.parseMediaType("text/csv;charset=UTF-8"));

    private final String extension;
    private final MediaType mediaType;

    PaymentExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    /** Aceita "xlsx" ou "csv" (o parâmetro da URL é minúsculo). */
    public static PaymentExportFormat from(String value) {
        for (PaymentExportFormat format : values()) {
            if (format.extension.equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("format must be xlsx or csv");
    }
}
