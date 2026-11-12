package br.com.familhas.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.common.Money;
import br.com.familhas.payment.PaymentGateway.CheckoutRequest;
import br.com.familhas.payment.PaymentGateway.CheckoutSession;
import br.com.familhas.payment.dto.CheckoutSessionResponse;

/**
 * Abre o Stripe Checkout para pagar uma reserva (ADR 0007, D1 e D5). Idempotente pelo domínio:
 *
 * - sessão aberta com o mesmo método: devolve a mesma (created = false);
 * - sessão aberta com outro método: expira a anterior no Stripe e cria outra;
 * - Pix em processamento: 409, para não cobrar duas vezes.
 *
 * A reserva fica travada durante a chamada ao Stripe: duas requisições simultâneas não abrem duas sessões.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;
    private final Clock clock;

    public CheckoutService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
            PaymentGateway gateway, PaymentProperties properties, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    /** @param response sessão para redirecionar; created = false quando a sessão aberta foi reaproveitada */
    public record CheckoutResult(CheckoutSessionResponse response, boolean created) {
    }

    @Transactional
    public CheckoutResult open(UUID clientId, UUID bookingId, PaymentMethod method) {
        Instant now = clock.instant();
        BookingEntity booking = bookingRepository.lockById(bookingId)
                .filter(b -> b.getClient().getId().equals(clientId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(HttpStatus.CONFLICT, "BOOKING_NOT_PAYABLE",
                    "Esta reserva nao esta aguardando pagamento.");
        }
        if (method == PaymentMethod.PIX && !properties.pixEnabled()) {
            throw new PaymentMethodUnavailableException(method);
        }
        if (!gateway.isAvailable()) {
            throw new PaymentGatewayException("Stripe nao configurado");
        }

        List<PaymentEntity> attempts = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        if (attempts.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PROCESSING)) {
            throw new BusinessException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                    "Ja existe um pagamento em processamento para esta reserva.");
        }
        Optional<PaymentEntity> open = attempts.stream().filter(p -> p.hasOpenSession(now)).findFirst();
        if (open.isPresent() && open.get().getMethod() == method) {
            return new CheckoutResult(toResponse(open.get()), false);
        }
        if (!booking.isPaymentWindowOpen(now)) {
            throw new BusinessException(HttpStatus.CONFLICT, "BOOKING_EXPIRED",
                    "O prazo para pagar esta reserva terminou.");
        }
        for (PaymentEntity previous : attempts) {
            if (previous.hasOpenSession(now)) {
                gateway.expireCheckoutSession(previous.getStripeCheckoutSessionId(),
                        previous.idempotencyKeyFor("expire"));
            }
            if (previous.getStatus() == PaymentStatus.PENDING || previous.getStatus() == PaymentStatus.FAILED) {
                previous.markCanceled();
            }
        }

        PaymentEntity payment = paymentRepository.saveAndFlush(PaymentEntity.pending(booking, method));
        CheckoutSession session = gateway.createCheckoutSession(new CheckoutRequest(
                payment.getId(),
                booking.getId(),
                method,
                new Money(payment.getAmount(), payment.getCurrency()),
                "Reserva Familhas " + booking.getId().toString().substring(0, 8).toUpperCase(),
                booking.getClient().getEmail(),
                properties.returnUrl(booking.getId(), "sucesso"),
                properties.returnUrl(booking.getId(), "cancelado"),
                now.plus(properties.checkoutSessionTtl()),
                payment.getIdempotencyKey()));
        payment.setStripeCheckoutSessionId(session.id());
        payment.setCheckoutUrl(session.url());
        payment.setSessionExpiresAt(session.expiresAt());
        log.info("Checkout aberto: bookingId={} paymentId={} method={}", booking.getId(), payment.getId(), method);
        return new CheckoutResult(toResponse(payment), true);
    }

    private static CheckoutSessionResponse toResponse(PaymentEntity payment) {
        return new CheckoutSessionResponse(payment.getId(), payment.getMethod(), payment.getCheckoutUrl(),
                payment.getSessionExpiresAt());
    }
}
