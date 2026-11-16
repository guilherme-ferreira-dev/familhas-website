import type { Company, Itinerary } from '../../core/api/crm-types';

export const DEFAULT_PAYMENT_TERMS = 'PIX ou cartão em 10x sem juros';

/**
 * Texto do roteiro para o WhatsApp do cliente. Nunca leva os alertas internos. Segue o modelo mestre (sem horários,
 * voos, companhias ou estrelas: a tela avisa antes).
 */
export function itineraryWhatsappText(it: Itinerary, company: Company | null): string {
  const s = it.sections;
  const lines = [`*${it.destination}* · ${it.days} ${it.days === 1 ? 'dia' : 'dias'}`];
  if (it.travelPeriod) lines.push(it.travelPeriod);
  if (s.overview) lines.push('', s.overview);
  if (s.days?.length) {
    lines.push('', '*Roteiro*');
    for (const d of s.days)
      lines.push(
        `Dia ${d.day}${d.title ? ` · ${d.title}` : ''}${d.description ? `: ${d.description}` : ''}`,
      );
  }
  if (s.included?.length)
    lines.push('', '*O que está incluso*', ...s.included.map((i) => `✅ ${i}`));
  lines.push('', '*Investimento*');
  if (it.priceText) lines.push(it.priceText);
  lines.push(it.paymentTerms || DEFAULT_PAYMENT_TERMS);
  if (s.investmentNotes) lines.push(s.investmentNotes);
  if (it.observations) lines.push('', it.observations);
  if (company)
    lines.push('', `${company.tradeName} · WhatsApp ${company.whatsapp} · ${company.site}`);
  return lines.join('\n');
}
