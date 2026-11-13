package br.com.familhas.admin.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentStatus;

public final class PaymentFixtures {

    private PaymentFixtures() {
    }

    public static AdminPaymentResponse payment(String clientName, String amount) {
        return new AdminPaymentResponse(
                UUID.fromString("9a000000-0000-4000-8000-000000000001"),
                UUID.fromString("b0000000-0000-4000-8000-000000000001"),
                new ClientRef(UUID.fromString("c0000000-0000-4000-8000-000000000001"), clientName, "ana@example.com"),
                PaymentMethod.CREDIT_CARD, PaymentStatus.SUCCEEDED, 1, new BigDecimal(amount),
                new BigDecimal("0.00"), "BRL", Instant.parse("2027-01-10T15:30:00Z"), null, "pi_123",
                Instant.parse("2027-01-10T15:00:00Z"), Instant.parse("2027-01-10T15:30:05Z"));
    }
}
