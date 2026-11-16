import type { Itinerary } from '../../core/api/crm-types';
import { itineraryWhatsappText } from './itinerary-text';
import { resizeDays } from './itinerary.page';

const IT: Itinerary = {
  id: 'r1',
  destination: 'Maceió',
  days: 2,
  clientName: 'Ana',
  clientPhone: '(61) 98888-7777',
  travelPeriod: '29 de Jan a 01 de Fev',
  priceText: 'R$ 4.980 para 2 pessoas',
  paymentTerms: null,
  observations: 'Incluir buggy',
  internalAlerts: 'Cliente pediu desconto: não prometer',
  sections: {
    overview: 'Mar verde.',
    days: [{ day: 1, title: 'Chegada', description: 'Acomodação.' }],
    included: ['Hospedagem com café'],
    investmentNotes: null,
  },
  photos: [],
  warnings: [],
  createdAt: '2026-11-16T10:00:00Z',
  updatedAt: '2026-11-16T10:00:00Z',
  version: 0,
};

describe('Gerar Roteiro', () => {
  it('ajusta os dias sem perder o que foi escrito', () => {
    const days = resizeDays([{ day: 2, title: 'Gunga', description: 'Praia' }], 3);
    expect(days.map((d) => d.day)).toEqual([1, 2, 3]);
    expect(days[1].title).toBe('Gunga');
    expect(resizeDays(days, 1)).toHaveLength(1);
    expect(resizeDays([], 99)).toHaveLength(30);
  });

  it('WhatsApp com as quatro seções e sem alertas internos', () => {
    const text = itineraryWhatsappText(IT, null);
    expect(text).toContain('*Maceió* · 2 dias');
    expect(text).toContain('Dia 1 · Chegada: Acomodação.');
    expect(text).toContain('✅ Hospedagem com café');
    expect(text).toContain(
      '*Investimento*\nR$ 4.980 para 2 pessoas\nPIX ou cartão em 10x sem juros',
    );
    expect(text).not.toContain('desconto');
  });
});
