package br.com.familhas.crm.voucher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Linha do histórico de vouchers. */
public record VoucherSummary(UUID id, String number, VoucherType type, VoucherStatus status, String clientName,
        String provider, LocalDate serviceDate, String issuedBy, Instant issuedAt) {
}
