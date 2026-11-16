package br.com.familhas.crm.voucher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.crm.file.CrmFileResponse;

/** Voucher para a tela, com CPF mascarado. O CPF inteiro só sai no documento para impressão (auditado). */
public record VoucherResponse(UUID id, String number, VoucherType type, VoucherStatus status, String clientName,
        String clientCpfMasked, String clientPhone, String notes, VoucherDetails details, LocalDate serviceDate,
        CrmFileResponse sourceFile, String issuedBy, Instant issuedAt, Instant cancelledAt, long version) {
}
