package br.com.familhas.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.familhas.email.EmailSender;

class CheckoutEmailsTests {

    private static final UUID BOOKING = UUID.fromString("8d0e4c7a-31b2-4f0f-9c64-5b0a2e7d4f10");

    private final EmailSender sender = mock(EmailSender.class);
    private final CheckoutEmails emails = new CheckoutEmails(sender);

    @Test
    void bookingCreatedShowsAmountReferenceAndDeadlineInBrasilia() {
        emails.bookingCreated(new BookingCreatedEvent(UUID.randomUUID(), BOOKING, UUID.randomUUID(),
                "ana@exemplo.com", "Ana", "7830.16", "BRL", Instant.parse("2026-11-12T15:30:00Z"), Instant.now(), 1));

        String body = sent(CheckoutEmails.BOOKING_CREATED_SUBJECT);
        assertThat(body).startsWith("Olá, Ana!")
                .contains("8D0E4C7A")
                .contains("R$ 7.830,16")
                .contains("12/11/2026 às 12:30 (horário de Brasília)");
    }

    @Test
    void paymentConfirmedNamesTheMethod() {
        emails.paymentConfirmed(new PaymentConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), BOOKING,
                UUID.randomUUID(), "ana@exemplo.com", null, "PIX", 1, "100.00", "BRL", Instant.now(), Instant.now(), 1));

        assertThat(sent(CheckoutEmails.PAYMENT_CONFIRMED_SUBJECT)).startsWith("Olá!")
                .contains("R$ 100,00 (Pix)")
                .contains("está confirmada");
    }

    @Test
    void paymentFailedNeverShowsTheStripeDeclineCode() {
        emails.paymentFailed(new PaymentFailedEvent(UUID.randomUUID(), UUID.randomUUID(), BOOKING, UUID.randomUUID(),
                "ana@exemplo.com", "Ana", "CREDIT_CARD", "100.00", "BRL", "insufficient_funds", Instant.now(), 1));

        assertThat(sent(CheckoutEmails.PAYMENT_FAILED_SUBJECT))
                .contains("cartão de crédito")
                .contains("nada foi cobrado")
                .doesNotContain("insufficient_funds");
    }

    private String sent(String subject) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq("ana@exemplo.com"), eq(subject), body.capture());
        return body.getValue();
    }
}
