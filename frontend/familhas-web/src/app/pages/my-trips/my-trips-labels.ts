import type { PaymentStatus, RecommendationReason, Trip } from '../../core/api/api-types';

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Aguardando pagamento',
  PROCESSING: 'Pagamento em processamento',
  SUCCEEDED: 'Pago',
  FAILED: 'Pagamento recusado',
  CANCELED: 'Pagamento cancelado',
  REFUNDED: 'Estornado',
  PARTIALLY_REFUNDED: 'Estornado em parte',
};

export const REASON_LABELS: Record<RecommendationReason, string> = {
  SIMILAR_CATEGORY: 'Combina com suas viagens',
  SIMILAR_DESTINATION: 'Destino que você curte',
  SEASONAL: 'Na sua época de viajar',
  HIGHLY_RATED: 'Bem avaliado',
  ON_PROMOTION: 'Em promoção',
};

/** Selo do status da viagem para a lista. */
export function tripBadge(trip: Trip): string {
  if (trip.timing === 'PAST') return 'Realizada';
  return trip.inProgress ? 'Em andamento' : 'Confirmada';
}

export const RATING_OPTIONS = [1, 2, 3, 4, 5] as const;
