package br.com.familhas.crm.company;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Dados fixos da agência (uma só: não há cadastro de empresas). Saem do application.properties para não ficarem
 * espalhados no código; vouchers, roteiros, posts e a lateral do CRM leem daqui (ADR 0014).
 *
 * Campo vazio = ainda não informado pela agência; a tela mostra "a definir" em vez de inventar um valor.
 */
@ConfigurationProperties("familhas.company")
public record CompanyProperties(
        @DefaultValue("EEBPG Agência de Viagens LTDA") String legalName,
        @DefaultValue("Voe Familhas") String tradeName,
        @DefaultValue("62.845.580/0001-75") String cnpj,
        @DefaultValue("familhas.tur.br") String site,
        @DefaultValue("(61) 99990-9464") String whatsapp,
        @DefaultValue("") String email,
        @DefaultValue("") String instagram) {
}
