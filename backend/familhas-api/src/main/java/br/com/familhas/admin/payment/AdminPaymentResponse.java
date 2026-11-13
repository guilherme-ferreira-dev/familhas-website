package br.com.familhas.admin.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentStatus;

/** Uma linha da planilha de pagamentos. */
public record AdminPaymentResponse(
        UUID id,
        UUID bookingId,
        ClientRef client,
        PaymentMethod method,
        PaymentStatus status,
        int installments,
        BigDecimal amount,
        BigDecimal refundedAmount,
        String currency,
        Instant paidAt,
        String failureCode,
        String stripePaymentIntentId,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminPaymentResponse of(PaymentEntity p) {
        return new AdminPaymentResponse(p.getId(), p.getBooking().getId(), ClientRef.of(p.getBooking().getClient()),
                p.getMethod(), p.getStatus(), p.getInstallments(), p.getAmount(), p.getRefundedAmount(),
                p.getCurrency(), p.getPaidAt(), p.getFailureCode(), p.getStripePaymentIntentId(), p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
