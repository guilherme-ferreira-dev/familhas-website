package br.com.familhas.crm.voucher;

/**
 * Extração por IA dos dados do serviço a partir do PDF/imagem de origem. Ainda sem implementação: o provedor de IA
 * não foi escolhido (ADR 0014). Quando entrar: mandar só o documento, nunca pedir nome/CPF/telefone ao provedor
 * (esses campos são sempre manuais), e devolver os campos para revisão humana antes de emitir.
 */
public interface VoucherExtractor {

    VoucherDetails extract(VoucherType type, byte[] document, String contentType);
}
