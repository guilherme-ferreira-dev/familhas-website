/** Regras de texto dos posts de cotação e de cruzeiros (JSON, "Criar Posts de cotação"). Puras e testadas. */

export const DEFAULT_PAYMENT = 'PIX ou CARTÃO EM 10X SEM JUROS';
export const DEFAULT_OBSERVATION = 'Saída de Brasília-DF';
export const RENTED_CAR_TEXT = 'Carro Alugado incluso';
export const TRANSFER_TEXT = 'Traslado incluso';

const MONTHS = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

/**
 * "29 de Jan a 04 de Fev" quando os meses diferem; "10 a 15 de Jan" no mesmo mês; "10 de Jan" num dia só.
 * Datas no formato yyyy-MM-dd, sem fuso.
 */
export function formatDateRange(
  start: string | null | undefined,
  end: string | null | undefined,
): string {
  if (!start) return '';
  const [, sm, sd] = start.split('-');
  const startDay = sd.padStart(2, '0');
  const startMonth = MONTHS[Number(sm) - 1];
  if (!end || end === start) return `${startDay} de ${startMonth}`;
  const [, em, ed] = end.split('-');
  const endDay = ed.padStart(2, '0');
  const endMonth = MONTHS[Number(em) - 1];
  return sm === em
    ? `${startDay} a ${endDay} de ${startMonth}`
    : `${startDay} de ${startMonth} a ${endDay} de ${endMonth}`;
}

/** Valor total dividido pelas pessoas (1 a 10, padrão 2), arredondado para cima no centavo (nunca anuncia menos). */
export function pricePerPerson(
  total: number | null | undefined,
  people: number | null | undefined,
): number | null {
  if (total === null || total === undefined || !Number.isFinite(total) || total <= 0) return null;
  const n = Math.min(10, Math.max(1, Math.trunc(people ?? 2)));
  return Math.ceil((total * 100) / n) / 100;
}

/** "R$ 2.490,00" */
export function brl(value: number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

/** Rótulo do valor: em apto duplo quando são 2 pessoas (padrão do JSON). */
export function perPersonLabel(people: number | null | undefined): string {
  return (people ?? 2) === 2 ? 'Valor por pessoa em apto duplo' : 'Valor por pessoa';
}

export interface QuoteInclusions {
  readonly flight: boolean;
  readonly hotel: string | null;
  readonly board: string | null;
  readonly transfer: boolean;
  readonly rentedCar: boolean;
  /** Ingressos, passeios, seguro viagem... um por linha. */
  readonly extras: readonly string[];
}

/** Itens do card. Carro alugado = Sim troca o traslado por "Carro Alugado incluso" em todos os modelos. */
export function inclusionLines(i: QuoteInclusions): string[] {
  const lines: string[] = [];
  if (i.flight) lines.push('Aéreo ida e volta');
  if (i.hotel) lines.push(i.board ? `${i.hotel} · ${i.board}` : i.hotel);
  if (i.rentedCar) lines.push(RENTED_CAR_TEXT);
  else if (i.transfer) lines.push(TRANSFER_TEXT);
  for (const extra of i.extras) {
    const text = extra.trim();
    if (text && text !== TRANSFER_TEXT) lines.push(text);
  }
  return lines;
}

/** Legenda pronta para o Instagram a partir do post de cotação (sem IA: modelo fixo). */
export function quoteCaption(input: {
  destination: string;
  dates: string;
  perPerson: number | null;
  people: number;
  lines: readonly string[];
  payment: string;
  observation: string;
  whatsapp: string;
}): string {
  const parts = [
    `✈️ ${input.destination.toUpperCase()}${input.dates ? ` · ${input.dates}` : ''}`,
    '',
    ...input.lines.map((l) => `✅ ${l}`),
    '',
  ];
  if (input.perPerson !== null) {
    parts.push(`💰 ${brl(input.perPerson)} ${perPersonLabel(input.people).toLowerCase()}`);
  }
  parts.push(
    input.payment,
    input.observation,
    '',
    `Chame no WhatsApp ${input.whatsapp} e garanta a sua vaga!`,
  );
  return parts.join('\n');
}
