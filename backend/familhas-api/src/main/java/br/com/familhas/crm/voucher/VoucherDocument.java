package br.com.familhas.crm.voucher;

import br.com.familhas.crm.company.CompanyProperties;

/**
 * Tudo o que o layout A4 precisa: cabeçalho fixo da empresa e o voucher com o CPF inteiro e formatado, que o
 * cliente precisa apresentar. Cada emissão do documento vai para a auditoria.
 */
public record VoucherDocument(CompanyProperties company, VoucherResponse voucher, String clientCpfFormatted) {
}
