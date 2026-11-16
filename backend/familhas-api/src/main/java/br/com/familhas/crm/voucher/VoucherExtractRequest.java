package br.com.familhas.crm.voucher;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record VoucherExtractRequest(@NotNull VoucherType type, @NotNull UUID fileId) {
}
