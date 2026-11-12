package br.com.familhas.booking;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param paymentWindow prazo para pagar a reserva, com os assentos bloqueados (ADR 0007, D3; padrão 30 min) */
@ConfigurationProperties(prefix = "familhas.bookings")
public record BookingProperties(Duration paymentWindow) {

    public BookingProperties {
        paymentWindow = paymentWindow == null ? Duration.ofMinutes(30) : paymentWindow;
    }
}
