import type { VoucherDetails, VoucherType } from '../../core/api/crm-types';

export type DetailKey = Exclude<keyof VoucherDetails, 'passengers'>;

export interface DetailField {
  readonly key: DetailKey;
  readonly label: string;
  readonly kind: 'text' | 'date' | 'time' | 'number' | 'textarea';
  readonly required?: boolean;
  readonly max?: number;
}

const COMMON_TAIL: DetailField[] = [
  { key: 'adults', label: 'Adultos', kind: 'number' },
  { key: 'children', label: 'Crianças', kind: 'number' },
  { key: 'inclusions', label: 'Inclui', kind: 'textarea', max: 1000 },
];

/** Campos de cada tipo, na ordem do voucher. Obrigatórios iguais aos da API (VoucherDetails.missingFor). */
export const VOUCHER_FIELDS: Record<VoucherType, readonly DetailField[]> = {
  FLIGHT: [
    { key: 'provider', label: 'Companhia aérea', kind: 'text', required: true, max: 120 },
    { key: 'reservationCode', label: 'Localizador', kind: 'text', max: 30 },
    { key: 'origin', label: 'Origem', kind: 'text', required: true, max: 120 },
    { key: 'destination', label: 'Destino', kind: 'text', required: true, max: 120 },
    { key: 'serviceDate', label: 'Data da ida', kind: 'date', required: true },
    { key: 'startTime', label: 'Horário da ida', kind: 'time' },
    { key: 'flightNumber', label: 'Voo de ida', kind: 'text', max: 20 },
    { key: 'endDate', label: 'Data da volta', kind: 'date' },
    { key: 'endTime', label: 'Horário da volta', kind: 'time' },
    { key: 'returnFlightNumber', label: 'Voo de volta', kind: 'text', max: 20 },
    ...COMMON_TAIL,
  ],
  HOTEL: [
    { key: 'provider', label: 'Hotel', kind: 'text', required: true, max: 120 },
    { key: 'location', label: 'Endereço', kind: 'text', max: 200 },
    { key: 'reservationCode', label: 'Código da reserva', kind: 'text', max: 30 },
    { key: 'serviceDate', label: 'Check-in', kind: 'date', required: true },
    { key: 'startTime', label: 'Horário do check-in', kind: 'time' },
    { key: 'endDate', label: 'Check-out', kind: 'date', required: true },
    { key: 'endTime', label: 'Horário do check-out', kind: 'time' },
    { key: 'roomType', label: 'Quarto', kind: 'text', max: 120 },
    { key: 'board', label: 'Regime', kind: 'text', max: 60 },
    ...COMMON_TAIL,
  ],
  TRANSFER: [
    { key: 'provider', label: 'Empresa', kind: 'text', max: 120 },
    { key: 'reservationCode', label: 'Código da reserva', kind: 'text', max: 30 },
    { key: 'origin', label: 'Saída de', kind: 'text', required: true, max: 120 },
    { key: 'destination', label: 'Chegada em', kind: 'text', required: true, max: 120 },
    { key: 'serviceDate', label: 'Data', kind: 'date', required: true },
    { key: 'startTime', label: 'Horário', kind: 'time' },
    { key: 'vehicle', label: 'Veículo', kind: 'text', max: 60 },
    ...COMMON_TAIL,
  ],
  TOUR: [
    { key: 'description', label: 'Passeio', kind: 'text', required: true, max: 300 },
    { key: 'provider', label: 'Operadora', kind: 'text', max: 120 },
    { key: 'location', label: 'Ponto de encontro', kind: 'text', max: 200 },
    { key: 'serviceDate', label: 'Data', kind: 'date', required: true },
    { key: 'startTime', label: 'Horário', kind: 'time' },
    { key: 'reservationCode', label: 'Código da reserva', kind: 'text', max: 30 },
    ...COMMON_TAIL,
  ],
};

/** Rótulo do campo no tipo (para o voucher impresso e a tela de detalhe). */
export function fieldLabel(type: VoucherType, key: DetailKey): string {
  return VOUCHER_FIELDS[type].find((f) => f.key === key)?.label ?? key;
}

/** Campos obrigatórios vazios (a API confere de novo). */
export function missingFields(type: VoucherType, details: VoucherDetails): string[] {
  return VOUCHER_FIELDS[type]
    .filter((f) => f.required)
    .filter((f) => {
      const value = details[f.key];
      return value === null || value === undefined || `${value}`.trim() === '';
    })
    .map((f) => f.label);
}
