import type { Company, Voucher, VoucherStatus, VoucherType } from '../core/api/crm-types';
import { formatDate } from './format';

/** Horário local do bilhete ("2026-12-10T08:30:00"), sem conversão de fuso: "10/12/2026 08:30". */
export function formatLocalDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const [day, time = ''] = value.split('T');
  return `${formatDate(day)} ${time.slice(0, 5)}`.trim();
}

/** Valor para <input type="datetime-local"> a partir do horário da API. */
export function toDateTimeInput(value: string | null | undefined): string {
  return value ? value.slice(0, 16) : '';
}

/** Valor do <input type="datetime-local"> para a API (segundos zerados). */
export function fromDateTimeInput(value: string | null | undefined): string | null {
  return value ? `${value.slice(0, 16)}:00` : null;
}

export const VOUCHER_TYPE: Record<VoucherType, string> = {
  FLIGHT: 'Voo',
  HOTEL: 'Hotel',
  TRANSFER: 'Transfer',
  TOUR: 'Passeio',
};

export const VOUCHER_STATUS: Record<VoucherStatus, string> = {
  ISSUED: 'Emitido',
  CANCELLED: 'Cancelado',
};

/** Telefone para o wa.me: só dígitos, com 55 quando o número é brasileiro sem DDI. Null se não der. */
export function whatsappNumber(phone: string | null | undefined): string | null {
  const digits = (phone ?? '').replace(/\D/g, '');
  if (digits.length === 10 || digits.length === 11) return `55${digits}`;
  if (digits.length >= 12 && digits.length <= 15) return digits;
  return null;
}

/**
 * Texto do voucher para o WhatsApp do cliente. Nunca leva CPF (LGPD): só o que o cliente precisa para usar o
 * serviço. O PDF/impressão com o CPF fica na agência.
 */
export function voucherWhatsappText(voucher: Voucher, company: Company | null): string {
  const d = voucher.details;
  const lines = [
    `*${company?.tradeName ?? 'Voe Familhas'}* · Voucher ${voucher.number}`,
    `${VOUCHER_TYPE[voucher.type]} · ${voucher.clientName}`,
  ];
  const push = (label: string, value: string | number | null | undefined) => {
    if (value !== null && value !== undefined && `${value}`.trim() !== '')
      lines.push(`${label}: ${value}`);
  };
  push('Fornecedor', d.provider);
  push('Reserva', d.reservationCode);
  push('Origem', d.origin);
  push('Destino', d.destination);
  push('Local', d.location);
  push(
    'Data',
    d.serviceDate ? formatDate(d.serviceDate) + (d.startTime ? ` ${d.startTime}` : '') : null,
  );
  push('Até', d.endDate ? formatDate(d.endDate) + (d.endTime ? ` ${d.endTime}` : '') : null);
  push('Voo', d.flightNumber);
  push('Voo de volta', d.returnFlightNumber);
  push('Quarto', d.roomType);
  push('Regime', d.board);
  push('Veículo', d.vehicle);
  push('Serviço', d.description);
  push('Inclui', d.inclusions);
  push('Observações', voucher.notes);
  if (company) lines.push('', `Dúvidas: WhatsApp ${company.whatsapp} · ${company.site}`);
  return lines.join('\n');
}

export function whatsappLink(phone: string | null | undefined, text: string): string | null {
  const number = whatsappNumber(phone);
  return number ? `https://wa.me/${number}?text=${encodeURIComponent(text)}` : null;
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}
