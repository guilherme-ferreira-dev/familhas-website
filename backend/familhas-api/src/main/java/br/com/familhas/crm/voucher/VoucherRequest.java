package br.com.familhas.crm.voucher;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Emitir ou corrigir voucher. NOME, CPF, TELEFONE e OBSERVAÇÕES são sempre digitados pela equipe. Na edição, CPF
 * null mantém o guardado (a tela só tem o mascarado) e vazio apaga. {@code version} obrigatório na edição.
 */
public record VoucherRequest(
        @NotNull VoucherType type,
        @NotBlank @Size(max = 150) String clientName,
        @Size(max = 14) String clientCpf,
        @Size(max = 20) @Pattern(regexp = "^[0-9 ()+-]*$", message = "telefone invalido") String clientPhone,
        @Size(max = 2000) String notes,
        @NotNull @Valid VoucherDetails details,
        UUID sourceFileId,
        Long version) {
}
