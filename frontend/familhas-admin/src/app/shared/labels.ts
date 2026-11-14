import type {
  AuditAction,
  BoardType,
  BookingStatus,
  CarCategory,
  CityType,
  EntityStatus,
  PackageComponentType,
  PaymentMethod,
  PaymentStatus,
  PriceItemType,
  QuoteOrigin,
  QuoteStatus,
  Transmission,
} from '../core/api/api-types';

export type Tone = 'success' | 'warning' | 'danger' | 'neutral' | 'info';

export const BOOKING_STATUS: Record<BookingStatus, { label: string; tone: Tone }> = {
  PENDING_PAYMENT: { label: 'Aguardando pagamento', tone: 'warning' },
  CONFIRMED: { label: 'Confirmada', tone: 'success' },
  CANCELLED: { label: 'Cancelada', tone: 'danger' },
  COMPLETED: { label: 'Concluída', tone: 'neutral' },
};

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pendente', tone: 'warning' },
  PROCESSING: { label: 'Processando', tone: 'info' },
  SUCCEEDED: { label: 'Aprovado', tone: 'success' },
  FAILED: { label: 'Recusado', tone: 'danger' },
  CANCELED: { label: 'Cancelado', tone: 'neutral' },
  REFUNDED: { label: 'Estornado', tone: 'danger' },
  PARTIALLY_REFUNDED: { label: 'Estorno parcial', tone: 'warning' },
};

export const QUOTE_STATUS: Record<QuoteStatus, { label: string; tone: Tone }> = {
  OPEN: { label: 'Válida', tone: 'info' },
  EXPIRED: { label: 'Expirada', tone: 'neutral' },
  BOOKED: { label: 'Virou reserva', tone: 'success' },
};

export const ENTITY_STATUS: Record<EntityStatus, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Ativo', tone: 'success' },
  INACTIVE: { label: 'Inativo', tone: 'neutral' },
};

export const PAYMENT_METHOD: Record<PaymentMethod, string> = { PIX: 'Pix', CREDIT_CARD: 'Cartão' };
export const QUOTE_ORIGIN: Record<QuoteOrigin, string> = { CUSTOMER: 'Loja', ADMIN: 'Manual' };
export const PRICE_ITEM: Record<PriceItemType, string> = {
  FLIGHT: 'Voo',
  HOTEL_STAY: 'Hospedagem',
  CAR_RENTAL: 'Carro',
};
export const AUDIT_ACTION: Record<AuditAction, string> = {
  CREATE: 'Criou',
  UPDATE: 'Alterou',
  DELETE: 'Excluiu',
  EXPORT: 'Exportou',
};
export const CITY_TYPE: Record<CityType, string> = { ORIGIN: 'Origem', DESTINATION: 'Destino' };
export const BOARD_TYPE: Record<BoardType, string> = {
  ROOM_ONLY: 'Só hospedagem',
  BREAKFAST: 'Café da manhã',
  HALF_BOARD: 'Meia pensão',
  FULL_BOARD: 'Pensão completa',
  ALL_INCLUSIVE: 'All inclusive',
};
export const CAR_CATEGORY: Record<CarCategory, string> = {
  ECONOMY: 'Econômico',
  COMPACT: 'Compacto',
  SEDAN: 'Sedã',
  SUV: 'SUV',
  MINIVAN: 'Minivan',
  LUXURY: 'Luxo',
};
export const TRANSMISSION: Record<Transmission, string> = {
  MANUAL: 'Manual',
  AUTOMATIC: 'Automático',
};
export const COMPONENT_TYPE: Record<PackageComponentType, string> = {
  HOTEL_STAY: 'Hospedagem (noites)',
  CAR_RENTAL: 'Carro (dias)',
};

export function options<K extends string>(labels: Record<K, string | { label: string }>) {
  return (Object.keys(labels) as K[]).map((value) => {
    const label = labels[value];
    return { value, label: typeof label === 'string' ? label : label.label };
  });
}
