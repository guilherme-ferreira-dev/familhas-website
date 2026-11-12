package br.com.familhas.booking;

import java.util.Set;

/**
 * Máquina de estados da reserva (ADR 0007, D4).
 *
 * PENDING_PAYMENT -> CONFIRMED -> COMPLETED; PENDING_PAYMENT -> CANCELLED (prazo vencido);
 * CONFIRMED -> CANCELLED (estorno total).
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(BookingStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<BookingStatus> allowedTargets() {
        return switch (this) {
            case PENDING_PAYMENT -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(COMPLETED, CANCELLED);
            case CANCELLED, COMPLETED -> Set.of();
        };
    }
}
