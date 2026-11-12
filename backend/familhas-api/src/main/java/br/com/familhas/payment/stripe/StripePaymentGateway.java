package br.com.familhas.payment.stripe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.AllowedPaymentMethodType;
import com.stripe.param.checkout.SessionExpireParams;
import com.stripe.param.checkout.SessionRetrieveParams;

import br.com.familhas.common.Money;
import br.com.familhas.payment.InvalidWebhookException;
import br.com.familhas.payment.PaymentGateway;
import br.com.familhas.payment.PaymentGatewayException;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentMethodUnavailableException;
import br.com.familhas.payment.PaymentNotification;
import br.com.familhas.payment.PaymentNotification.Kind;
import br.com.familhas.payment.PaymentProperties;

/**
 * Adapter do Stripe Checkout hospedado (ADR 0007).
 *
 * - Uma Checkout Session por tentativa, com metadata bookingId/paymentId na sessão e no PaymentIntent.
 * - Toda escrita leva a idempotency key do pagamento; o SDK repete falhas de rede com a mesma key.
 * - O Stripe recebe só valor, descrição, e-mail (recibo) e ids internos. Nada disso vai para o log.
 * - Valores em centavos, sem arredondamento: valor com mais de 2 casas é erro.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    static final String METADATA_BOOKING_ID = "bookingId";
    static final String METADATA_PAYMENT_ID = "paymentId";

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final StripeProperties properties;
    private final PaymentProperties paymentProperties;
    private final StripeClient client;

    public StripePaymentGateway(StripeProperties properties, PaymentProperties paymentProperties) {
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.client = properties.apiConfigured() ? buildClient(properties) : null;
        if (client == null) {
            log.warn("Stripe sem STRIPE_SECRET_KEY: checkout indisponivel (503)");
        }
    }

    private static StripeClient buildClient(StripeProperties properties) {
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setConnectTimeout((int) properties.timeout().toMillis())
                .setReadTimeout((int) properties.timeout().toMillis())
                .setMaxNetworkRetries(properties.maxNetworkRetries());
        if (properties.apiBase() != null) {
            builder.setApiBase(properties.apiBase());
        }
        return builder.build();
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        requireClient();
        Money amount = request.amount();
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setLocale(SessionCreateParams.Locale.PT_BR)
                .setClientReferenceId(request.bookingId().toString())
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .setExpiresAt(request.expiresAt().getEpochSecond())
                .putMetadata(METADATA_BOOKING_ID, request.bookingId().toString())
                .putMetadata(METADATA_PAYMENT_ID, request.paymentId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(amount.currency().toLowerCase(Locale.ROOT))
                                .setUnitAmount(toMinorUnits(amount.amount()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.description())
                                        .build())
                                .build())
                        .build())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setDescription(request.description())
                        .putMetadata(METADATA_BOOKING_ID, request.bookingId().toString())
                        .putMetadata(METADATA_PAYMENT_ID, request.paymentId().toString())
                        .build());
        if (request.customerEmail() != null) {
            params.setCustomerEmail(request.customerEmail());
        }
        if (request.method() == PaymentMethod.PIX) {
            params.addAllowedPaymentMethodType(AllowedPaymentMethodType.PIX)
                    .setPaymentMethodOptions(SessionCreateParams.PaymentMethodOptions.builder()
                            .setPix(SessionCreateParams.PaymentMethodOptions.Pix.builder()
                                    .setExpiresAfterSeconds(paymentProperties.checkoutSessionTtl().toSeconds())
                                    .build())
                            .build());
        } else {
            params.addAllowedPaymentMethodType(AllowedPaymentMethodType.CARD);
        }

        try {
            Session session = client.v1().checkout().sessions().create(params.build(), idempotent(request.idempotencyKey()));
            log.info("Checkout Session criada: paymentId={} sessionId={} method={}", request.paymentId(),
                    session.getId(), request.method());
            return new CheckoutSession(session.getId(), session.getUrl(), Instant.ofEpochSecond(session.getExpiresAt()));
        } catch (InvalidRequestException ex) {
            if (ex.getParam() != null && ex.getParam().contains("payment_method")) {
                log.warn("Metodo recusado pela conta Stripe: method={} code={} requestId={}", request.method(),
                        ex.getCode(), ex.getRequestId());
                throw new PaymentMethodUnavailableException(request.method());
            }
            throw failure("criar Checkout Session", ex);
        } catch (StripeException ex) {
            throw failure("criar Checkout Session", ex);
        }
    }

    @Override
    public void expireCheckoutSession(String sessionId, String idempotencyKey) {
        requireClient();
        try {
            client.v1().checkout().sessions().expire(sessionId,
                    SessionExpireParams.builder().build(), idempotent(idempotencyKey));
            log.info("Checkout Session expirada: sessionId={}", sessionId);
        } catch (InvalidRequestException ex) {
            // Já concluída ou já expirada: o webhook (ou a reconciliação) informa o estado real.
            log.info("Checkout Session nao expirada (ja encerrada): sessionId={} code={}", sessionId, ex.getCode());
        } catch (StripeException ex) {
            throw failure("expirar Checkout Session", ex);
        }
    }

    @Override
    public Optional<PaymentNotification> fetchCheckoutSessionState(String sessionId) {
        requireClient();
        try {
            Session session = client.v1().checkout().sessions().retrieve(sessionId,
                    SessionRetrieveParams.builder().addExpand("payment_intent").build());
            return stateOf(session);
        } catch (StripeException ex) {
            throw failure("consultar Checkout Session", ex);
        }
    }

    @Override
    public void refund(String paymentIntentId, String idempotencyKey) {
        requireClient();
        try {
            client.v1().refunds().create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build(),
                    idempotent(idempotencyKey));
            log.warn("Estorno solicitado: paymentIntentId={}", paymentIntentId);
        } catch (StripeException ex) {
            throw failure("estornar", ex);
        }
    }

    @Override
    public PaymentNotification parseWebhook(String payload, String signatureHeader) {
        if (!properties.webhookConfigured()) {
            throw new InvalidWebhookException("STRIPE_WEBHOOK_SECRET nao configurado");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookException("Stripe-Signature ausente");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, properties.webhookSecret(),
                    properties.webhookTolerance().toSeconds());
        } catch (SignatureVerificationException ex) {
            throw new InvalidWebhookException("Assinatura do webhook invalida");
        } catch (RuntimeException ex) {
            throw new InvalidWebhookException("Corpo do webhook ilegivel");
        }
        return translate(event);
    }

    /** Evento do Stripe -> fato do domínio. Tipos não tratados viram IGNORED. */
    PaymentNotification translate(Event event) {
        String type = event.getType();
        return switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded",
                    "checkout.session.async_payment_failed", "checkout.session.expired" ->
                fromSession(event.getId(), type, (Session) object(event));
            case "payment_intent.processing", "payment_intent.succeeded", "payment_intent.payment_failed" ->
                fromPaymentIntent(event.getId(), type, (PaymentIntent) object(event));
            case "charge.refunded" -> fromCharge(event.getId(), type, (Charge) object(event));
            default -> PaymentNotification.ignored(event.getId(), type);
        };
    }

    private static PaymentNotification fromSession(String eventId, String type, Session session) {
        Kind kind = switch (type) {
            case "checkout.session.completed" -> "paid".equals(session.getPaymentStatus()) ? Kind.SUCCEEDED
                    : Kind.PROCESSING;
            case "checkout.session.async_payment_succeeded" -> Kind.SUCCEEDED;
            case "checkout.session.async_payment_failed" -> Kind.FAILED;
            default -> Kind.SESSION_EXPIRED;
        };
        return new PaymentNotification(eventId, type, kind, paymentIdOf(session.getMetadata()), session.getId(),
                session.getPaymentIntent(), fromMinorUnits(session.getAmountTotal()), null,
                upper(session.getCurrency()), kind == Kind.FAILED ? "async_payment_failed" : null);
    }

    private static PaymentNotification fromPaymentIntent(String eventId, String type, PaymentIntent intent) {
        Kind kind = switch (type) {
            case "payment_intent.succeeded" -> Kind.SUCCEEDED;
            case "payment_intent.processing" -> Kind.PROCESSING;
            default -> Kind.FAILED;
        };
        return new PaymentNotification(eventId, type, kind, paymentIdOf(intent.getMetadata()), null, intent.getId(),
                fromMinorUnits(intent.getAmountReceived()), null, upper(intent.getCurrency()),
                kind == Kind.FAILED ? failureCodeOf(intent) : null);
    }

    private static PaymentNotification fromCharge(String eventId, String type, Charge charge) {
        return new PaymentNotification(eventId, type, Kind.REFUNDED, paymentIdOf(charge.getMetadata()), null,
                charge.getPaymentIntent(), fromMinorUnits(charge.getAmount()),
                fromMinorUnits(charge.getAmountRefunded()), upper(charge.getCurrency()), null);
    }

    /** Estado da sessão consultada (reconciliação), no mesmo formato de um webhook. */
    static Optional<PaymentNotification> stateOf(Session session) {
        String type = "reconciliation";
        PaymentIntent intent = session.getPaymentIntentObject();
        String intentId = intent != null ? intent.getId() : session.getPaymentIntent();
        UUID paymentId = paymentIdOf(session.getMetadata());
        BigDecimal amount = fromMinorUnits(session.getAmountTotal());
        String currency = upper(session.getCurrency());
        if ("expired".equals(session.getStatus())) {
            return Optional.of(new PaymentNotification(null, type, Kind.SESSION_EXPIRED, paymentId, session.getId(),
                    intentId, amount, null, currency, null));
        }
        if ("paid".equals(session.getPaymentStatus())) {
            return Optional.of(new PaymentNotification(null, type, Kind.SUCCEEDED, paymentId, session.getId(),
                    intentId, amount, null, currency, null));
        }
        if (intent == null) {
            return Optional.empty();
        }
        Kind kind = switch (intent.getStatus()) {
            case "succeeded" -> Kind.SUCCEEDED;
            case "processing", "requires_action" -> "complete".equals(session.getStatus()) ? Kind.PROCESSING : null;
            case "canceled" -> Kind.FAILED;
            case "requires_payment_method" -> intent.getLastPaymentError() != null ? Kind.FAILED : null;
            default -> null;
        };
        if (kind == null) {
            return Optional.empty();
        }
        return Optional.of(new PaymentNotification(null, type, kind, paymentId, session.getId(), intentId,
                kind == Kind.SUCCEEDED ? fromMinorUnits(intent.getAmountReceived()) : amount, null, currency,
                kind == Kind.FAILED ? failureCodeOf(intent) : null));
    }

    /**
     * O objeto do evento. Se a versão da API do evento for diferente da do SDK (ex.: stripe listen com a versão
     * padrão da conta), getObject() vem vazio; os campos usados aqui existem nas duas, então a leitura segue.
     */
    private static StripeObject object(Event event) {
        return event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                throw new InvalidWebhookException("Objeto do evento ilegivel: " + event.getType());
            }
        });
    }

    private static String failureCodeOf(PaymentIntent intent) {
        if (intent.getLastPaymentError() == null) {
            return null;
        }
        String decline = intent.getLastPaymentError().getDeclineCode();
        return decline != null ? decline : intent.getLastPaymentError().getCode();
    }

    private static UUID paymentIdOf(Map<String, String> metadata) {
        if (metadata == null || metadata.get(METADATA_PAYMENT_ID) == null) {
            return null;
        }
        try {
            return UUID.fromString(metadata.get(METADATA_PAYMENT_ID));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Centavos, sem arredondamento: valor com mais de 2 casas lança ArithmeticException. */
    static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(Money.SCALE).longValueExact();
    }

    static BigDecimal fromMinorUnits(Long minor) {
        return minor == null ? null : BigDecimal.valueOf(minor, Money.SCALE);
    }

    private static String upper(String currency) {
        return currency == null ? null : currency.toUpperCase(Locale.ROOT);
    }

    private static RequestOptions idempotent(String key) {
        return RequestOptions.builder().setIdempotencyKey(key).build();
    }

    private void requireClient() {
        if (client == null) {
            throw new PaymentGatewayException("Stripe nao configurado (STRIPE_SECRET_KEY)");
        }
    }

    /** Só tipo, código e requestId do Stripe no log; a mensagem pode ecoar parâmetros enviados. */
    private static PaymentGatewayException failure(String operation, StripeException ex) {
        log.error("Falha no Stripe ao {}: type={} code={} status={} requestId={}", operation,
                ex.getClass().getSimpleName(), ex.getCode(), ex.getStatusCode(), ex.getRequestId());
        return new PaymentGatewayException("Falha no Stripe ao " + operation, ex);
    }
}
