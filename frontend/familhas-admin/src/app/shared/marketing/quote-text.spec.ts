import {
  DEFAULT_OBSERVATION,
  DEFAULT_PAYMENT,
  formatDateRange,
  inclusionLines,
  perPersonLabel,
  pricePerPerson,
  quoteCaption,
} from './quote-text';

describe('regras dos posts de cotação', () => {
  it('datas no formato do JSON', () => {
    expect(formatDateRange('2027-01-29', '2027-02-04')).toBe('29 de Jan a 04 de Fev');
    expect(formatDateRange('2027-01-10', '2027-01-15')).toBe('10 a 15 de Jan');
    expect(formatDateRange('2027-03-05', null)).toBe('05 de Mar');
    expect(formatDateRange(null, '2027-01-01')).toBe('');
  });

  it('divide pelo número de pessoas (1 a 10, padrão 2) sem anunciar a menos', () => {
    expect(pricePerPerson(4980, 2)).toBe(2490);
    expect(pricePerPerson(1000, 3)).toBe(333.34);
    expect(pricePerPerson(1000, null)).toBe(500);
    expect(pricePerPerson(1000, 50)).toBe(100);
    expect(pricePerPerson(1000, 0)).toBe(1000);
    expect(pricePerPerson(0, 2)).toBeNull();
    expect(perPersonLabel(2)).toBe('Valor por pessoa em apto duplo');
    expect(perPersonLabel(3)).toBe('Valor por pessoa');
  });

  it('carro alugado troca o traslado em todos os modelos', () => {
    const base = {
      flight: true,
      hotel: 'Hotel Ponta Verde',
      board: 'Café da manhã',
      transfer: true,
      extras: ['Seguro viagem', 'Traslado incluso'],
    };
    expect(inclusionLines({ ...base, rentedCar: false })).toEqual([
      'Aéreo ida e volta',
      'Hotel Ponta Verde · Café da manhã',
      'Traslado incluso',
      'Seguro viagem',
    ]);
    const withCar = inclusionLines({ ...base, rentedCar: true });
    expect(withCar).toContain('Carro Alugado incluso');
    expect(withCar).not.toContain('Traslado incluso');
  });

  it('textos padrão e legenda', () => {
    expect(DEFAULT_PAYMENT).toBe('PIX ou CARTÃO EM 10X SEM JUROS');
    expect(DEFAULT_OBSERVATION).toBe('Saída de Brasília-DF');
    const caption = quoteCaption({
      destination: 'Maceió',
      dates: '29 de Jan a 04 de Fev',
      perPerson: 2490,
      people: 2,
      lines: ['Aéreo ida e volta'],
      payment: DEFAULT_PAYMENT,
      observation: DEFAULT_OBSERVATION,
      whatsapp: '(61) 99990-9464',
    });
    expect(caption).toContain('MACEIÓ · 29 de Jan a 04 de Fev');
    expect(caption).toMatch(/2\.490,00 valor por pessoa em apto duplo/);
    expect(caption).toContain('(61) 99990-9464');
  });
});
