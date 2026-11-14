const ZONE = 'America/Sao_Paulo';

const dateTime = new Intl.DateTimeFormat('pt-BR', {
  timeZone: ZONE,
  dateStyle: 'short',
  timeStyle: 'short',
});
const date = new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC', dateStyle: 'short' });

/** Valor em dinheiro no formato brasileiro (R$ 7.830,16). Moeda sempre explícita. */
export function money(value: number | null | undefined, currency = 'BRL'): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value);
}

/** Instante da API (UTC) no horário de São Paulo. */
export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTime.format(new Date(value)) : '—';
}

/** Data sem horário (yyyy-MM-dd), sem conversão de fuso. */
export function formatDate(value: string | null | undefined): string {
  return value ? date.format(new Date(`${value}T00:00:00Z`)) : '—';
}

export function percent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(value)}%`;
}

/** Hoje em São Paulo, no formato yyyy-MM-dd (para inputs type=date e filtros da API). */
export function todayInSaoPaulo(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(now);
}

export function daysBefore(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - days);
  return d.toISOString().slice(0, 10);
}

/** 8 primeiros caracteres do UUID: o mesmo código curto que a Loja manda no WhatsApp. */
export function shortId(id: string | null | undefined): string {
  return id ? id.slice(0, 8) : '—';
}
