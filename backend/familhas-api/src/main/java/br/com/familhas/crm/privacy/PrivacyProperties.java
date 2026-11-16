package br.com.familhas.crm.privacy;

import java.time.Period;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * LGPD (ADR 0018). {@code voucherRetention}: prazo em que o voucher (documento do serviço contratado) não é
 * anonimizado. O padrão de 5 anos é uma premissa técnica a confirmar com o jurídico/contábil da agência
 * (pendência registrada em docs/lgpd.md); a anonimização é sempre manual, nunca automática.
 */
@ConfigurationProperties("familhas.privacy")
public record PrivacyProperties(@DefaultValue("P5Y") Period voucherRetention) {
}
