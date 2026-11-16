package br.com.familhas.crm.brand;

import br.com.familhas.crm.file.CrmFileResponse;

/** Logo oficial (null até alguém enviar). O conteúdo sai pelo link assinado do arquivo. */
public record BrandResponse(CrmFileResponse logo) {
}
