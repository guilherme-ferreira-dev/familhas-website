import type { Booking, PaymentMethod } from '../../core/api/api-types';

export const METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CREDIT_CARD: 'Cartão de crédito',
};

export function cancellationText(booking: Booking): string {
  switch (booking.cancellationReason) {
    case 'PAYMENT_EXPIRED':
      return 'A reserva foi cancelada porque o prazo para pagar acabou.';
    case 'REFUNDED':
      return 'A reserva foi cancelada e o valor foi estornado.';
    default:
      return 'A reserva foi cancelada.';
  }
}
