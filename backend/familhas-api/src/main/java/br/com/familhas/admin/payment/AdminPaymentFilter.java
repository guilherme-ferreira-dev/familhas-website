package br.com.familhas.admin.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Filtros da planilha de pagamentos e da exportação. Todos opcionais, combinados com AND. */
public record AdminPaymentFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        PaymentDateField dateField,
        List<PaymentStatus> status,
        PaymentMethod method,
        @Min(1) @Max(24) Integer installments,
        UUID clientId,
        UUID bookingId) {

    public static AdminPaymentFilter forClient(UUID clientId) {
        return new AdminPaymentFilter(null, null, null, null, null, null, clientId, null);
    }

    /** Texto curto dos filtros usados, para a auditoria da exportação. Só ids e enums, nenhum dado pessoal. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        append(text, "from", from);
        append(text, "to", to);
        append(text, "dateField", dateField);
        append(text, "status", status == null || status.isEmpty() ? null : status);
        append(text, "method", method);
        append(text, "installments", installments);
        append(text, "clientId", clientId);
        append(text, "bookingId", bookingId);
        return text.toString();
    }

    private static void append(StringBuilder text, String name, Object value) {
        if (value != null) {
            text.append(text.isEmpty() ? "" : " ").append(name).append('=').append(value);
        }
    }
}
