package br.com.familhas.admin.payment.export;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

import br.com.familhas.admin.payment.AdminPaymentResponse;
import br.com.familhas.common.BusinessTime;

/** Colunas da planilha exportada, na ordem. Datas no horário de São Paulo. */
final class PaymentExportColumns {

    enum Kind { TEXT, MONEY, INTEGER, DATE_TIME }

    record Column(String header, Kind kind, Function<AdminPaymentResponse, Object> value) {
    }

    static final List<Column> COLUMNS = List.of(
            new Column("Pagamento", Kind.TEXT, p -> p.id().toString()),
            new Column("Reserva", Kind.TEXT, p -> p.bookingId().toString()),
            new Column("Cliente", Kind.TEXT, p -> p.client().name()),
            new Column("E-mail", Kind.TEXT, p -> p.client().email()),
            new Column("Metodo", Kind.TEXT, p -> p.method().name()),
            new Column("Status", Kind.TEXT, p -> p.status().name()),
            new Column("Parcelas", Kind.INTEGER, AdminPaymentResponse::installments),
            new Column("Valor", Kind.MONEY, AdminPaymentResponse::amount),
            new Column("Estornado", Kind.MONEY, AdminPaymentResponse::refundedAmount),
            new Column("Moeda", Kind.TEXT, AdminPaymentResponse::currency),
            new Column("Pago em", Kind.DATE_TIME, p -> local(p.paidAt())),
            new Column("Falha", Kind.TEXT, AdminPaymentResponse::failureCode),
            new Column("Criado em", Kind.DATE_TIME, p -> local(p.createdAt())),
            new Column("Atualizado em", Kind.DATE_TIME, p -> local(p.updatedAt())),
            new Column("Stripe PaymentIntent", Kind.TEXT, AdminPaymentResponse::stripePaymentIntentId));

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PaymentExportColumns() {
    }

    static LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, BusinessTime.ZONE).withNano(0);
    }

    static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME.format(dateTime);
        }
        return value.toString();
    }
}
