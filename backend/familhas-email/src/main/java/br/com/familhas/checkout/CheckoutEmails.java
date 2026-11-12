package br.com.familhas.checkout;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.familhas.email.EmailSender;

/**
 * E-mails de reserva e pagamento (ADR 0007, D10). Texto simples, em português, com valores em reais e horários
 * de Brasília. Nada de dado de cartão: o código de recusa do Stripe não vai para o cliente.
 */
@Service
public class CheckoutEmails {

    static final String AMOUNT_PATTERN = "^[0-9]+\\.[0-9]{2}$";
    static final String CURRENCY_PATTERN = "^[A-Z]{3}$";
    static final String METHOD_PATTERN = "^(PIX|CREDIT_CARD)$";

    static final String BOOKING_CREATED_SUBJECT = "Sua reserva Familhas está aguardando pagamento";
    static final String PAYMENT_CONFIRMED_SUBJECT = "Pagamento aprovado: sua reserva está confirmada";
    static final String PAYMENT_FAILED_SUBJECT = "Pagamento não aprovado";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR);

    private final EmailSender emailSender;

    public CheckoutEmails(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void bookingCreated(BookingCreatedEvent event) {
        emailSender.sendText(event.email(), BOOKING_CREATED_SUBJECT, greeting(event.firstName()) + """


                Recebemos a sua reserva %s no valor de %s.

                Para garantir os lugares, conclua o pagamento até %s (horário de Brasília). \
                Depois desse prazo a reserva é cancelada automaticamente.

                Qualquer dúvida, fale com a nossa equipe pelo WhatsApp.

                Boas viagens,
                Equipe Familhas
                """.formatted(reference(event.bookingId()), money(event.totalAmount(), event.currency()),
                dateTime(event.expiresAt())));
    }

    public void paymentConfirmed(PaymentConfirmedEvent event) {
        emailSender.sendText(event.email(), PAYMENT_CONFIRMED_SUBJECT, greeting(event.firstName()) + """


                Recebemos o seu pagamento de %s (%s) e a reserva %s está confirmada.

                Você pode acompanhar a viagem em "Minhas viagens" na Loja.

                Boas viagens,
                Equipe Familhas
                """.formatted(money(event.amount(), event.currency()), methodName(event.method()),
                reference(event.bookingId())));
    }

    public void paymentFailed(PaymentFailedEvent event) {
        emailSender.sendText(event.email(), PAYMENT_FAILED_SUBJECT, greeting(event.firstName()) + """


                O pagamento de %s (%s) da reserva %s não foi aprovado e nada foi cobrado.

                Enquanto a reserva estiver no prazo, você pode tentar de novo pela Loja, \
                com outro cartão ou com Pix.

                Se precisar de ajuda, fale com a nossa equipe pelo WhatsApp.

                Equipe Familhas
                """.formatted(money(event.amount(), event.currency()), methodName(event.method()),
                reference(event.bookingId())));
    }

    static String greeting(String firstName) {
        return firstName == null || firstName.isBlank() ? "Olá!" : "Olá, " + firstName.strip() + "!";
    }

    /** Código curto que o cliente e o Enéas reconhecem (o mesmo da descrição no Stripe). */
    static String reference(UUID bookingId) {
        return bookingId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    static String money(String amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(PT_BR);
        format.setCurrency(Currency.getInstance(currency));
        return format.format(new BigDecimal(amount)).replace(' ', ' ');
    }

    static String dateTime(Instant instant) {
        return DATE_TIME.format(instant.atZone(BRASILIA));
    }

    static String methodName(String method) {
        return "PIX".equals(method) ? "Pix" : "cartão de crédito";
    }
}
