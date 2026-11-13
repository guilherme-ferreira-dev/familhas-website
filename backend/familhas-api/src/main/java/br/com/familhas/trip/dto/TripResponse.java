package br.com.familhas.trip.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.familhas.booking.BookingStatus;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentStatus;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;
import br.com.familhas.trip.TripTiming;

/** Viagem do cliente: leitura da reserva CONFIRMED ou COMPLETED (ADR 0010). O id é o da reserva. */
public record TripResponse(
        UUID id,
        BookingStatus status,
        TripTiming timing,
        boolean inProgress,
        LocalDate startDate,
        LocalDate endDate,
        String title,
        int passengers,
        PackageSummaryResponse travelPackage,
        List<Item> items,
        String currency,
        BigDecimal total,
        Instant confirmedAt,
        Payment payment,
        MyRating myRating,
        boolean canRate) {

    public record Item(PriceItemType type, String description, LocalDate startDate, LocalDate endDate,
            int quantity) {
    }

    public record Payment(PaymentMethod method, PaymentStatus status, int installments, BigDecimal amount,
            String currency, BigDecimal refundedAmount, Instant paidAt, List<Installment> installmentPlan) {
    }

    public record Installment(int number, BigDecimal amount, String currency) {
    }

    public record MyRating(UUID id, int rating) {
    }
}
