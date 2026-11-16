package br.com.familhas.crm.privacy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dados do titular encontrados pelo CPF, para responder o pedido de acesso (art. 18 da LGPD). Traz os dados como
 * estão, inclusive o CPF: é a resposta ao próprio titular. Cada emissão vai para a auditoria.
 */
public record SubjectReport(String cpf, Instant generatedAt, String generatedBy, List<Flight> flights,
        List<Voucher> vouchers) {

    public record Flight(UUID flightId, String fullName, String phone, String airline, String origin,
            String destination, LocalDateTime outboundAt, LocalDateTime returnAt, boolean archived) {
    }

    public record Voucher(UUID voucherId, String number, String type, String status, String clientName,
            String clientPhone, LocalDate serviceDate, Instant issuedAt) {
    }
}
