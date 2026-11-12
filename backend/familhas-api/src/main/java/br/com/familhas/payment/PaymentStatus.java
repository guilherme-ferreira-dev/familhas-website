package br.com.familhas.payment;

import java.util.Set;

/**
 * Máquina de estados do pagamento (ADR 0007, D4).
 *
 * PENDING: sessão de checkout aberta. PROCESSING: Pix gerado, aguardando. FAILED: recusado, mas o cliente
 * ainda pode tentar de novo na mesma sessão. CANCELED: sessão expirou ou foi substituída.
 *
 * CANCELED -> SUCCEEDED existe porque o Stripe manda: o cliente pode concluir a sessão antiga no instante em
 * que ela é substituída. O dinheiro entrou e precisa ser registrado (e estornado, se a reserva já estiver paga).
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<PaymentStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> Set.of(PROCESSING, SUCCEEDED, FAILED, CANCELED);
            case PROCESSING -> Set.of(SUCCEEDED, FAILED, CANCELED);
            case FAILED -> Set.of(PROCESSING, SUCCEEDED, CANCELED);
            case SUCCEEDED -> Set.of(PARTIALLY_REFUNDED, REFUNDED);
            case PARTIALLY_REFUNDED -> Set.of(PARTIALLY_REFUNDED, REFUNDED);
            case CANCELED -> Set.of(SUCCEEDED);
            case REFUNDED -> Set.of();
        };
    }
}
