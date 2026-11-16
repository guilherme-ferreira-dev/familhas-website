package br.com.familhas.crm.file;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Storage privado do CRM (ADR 0014).
 *
 * @param localRoot  pasta do volume privado; fora de qualquer pasta servida pela web
 * @param linkSecret segredo HMAC dos links assinados; vazio = aleatório por execução (links caem no restart;
 *                   com mais de uma instância é obrigatório)
 * @param linkTtl    validade do link assinado
 */
@ConfigurationProperties("familhas.storage")
public record StorageProperties(
        @DefaultValue("./var/crm-files") String localRoot,
        @DefaultValue("") String linkSecret,
        @DefaultValue("PT5M") Duration linkTtl) {
}
