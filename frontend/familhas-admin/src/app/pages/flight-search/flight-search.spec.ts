import { parseDestinations, validateSearch } from './flight-search.page';

describe('Pesquisar Voos: regras da tela', () => {
  const today = '2026-11-16';

  it('lê destinos digitados de qualquer jeito, sem repetição', () => {
    expect(parseDestinations(' mcz, rec;ssa  MCZ ')).toEqual(['MCZ', 'REC', 'SSA']);
    expect(parseDestinations('')).toEqual([]);
  });

  it('recusa antes de gastar consultas', () => {
    expect(
      validateSearch(
        {
          origin: 'BSB',
          destinations: ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'].map((x) => x + 'AA'),
          mode: 'FLEXIBLE',
          months: ['2026-12'],
        },
        today,
      ),
    ).toContain('No máximo 7 destinos por pesquisa.');
    expect(
      validateSearch(
        { origin: 'BSB', destinations: ['BSB'], mode: 'FLEXIBLE', months: ['2026-12'] },
        today,
      ),
    ).toContain('Destino igual à origem.');
    expect(
      validateSearch(
        {
          origin: 'BSB',
          destinations: ['MCZ'],
          mode: 'EXACT',
          departureDate: '2026-12-10',
          returnDate: '2026-12-01',
        },
        today,
      ),
    ).toEqual(['A ida precisa ser a partir de hoje e a volta depois da ida.']);
    expect(
      validateSearch(
        {
          origin: 'BSB',
          destinations: ['MCZ'],
          mode: 'FLEXIBLE',
          months: ['2026-12', '2027-01', '2027-02', '2027-03'],
        },
        today,
      ),
    ).toContain('Escolha de 1 a 3 meses.');
    expect(
      validateSearch(
        {
          origin: 'CXJ',
          destinations: ['GRU'],
          mode: 'FLEXIBLE',
          months: ['2026-12'],
          stayNights: 31,
        },
        today,
      ),
    ).toContain('Estadia de 1 a 30 noites.');
    expect(
      validateSearch(
        {
          origin: 'CXJ',
          destinations: ['GRU', 'MCZ'],
          mode: 'FLEXIBLE',
          months: ['2026-12'],
          stayNights: 5,
        },
        today,
      ),
    ).toEqual([]);
  });
});
