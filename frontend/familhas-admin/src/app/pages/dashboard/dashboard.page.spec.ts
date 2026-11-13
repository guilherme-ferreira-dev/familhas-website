import { compact, niceMax } from './revenue-chart';
import { eachDay } from './dashboard.page';

describe('dashboard', () => {
  it('preenche os dias sem venda', () => {
    expect(eachDay('2027-02-27', '2027-03-02')).toEqual([
      '2027-02-27',
      '2027-02-28',
      '2027-03-01',
      '2027-03-02',
    ]);
  });

  it('arredonda o topo do eixo', () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(7830.16)).toBe(10000);
    expect(niceMax(1800)).toBe(2000);
    expect(niceMax(2300)).toBe(2500);
  });

  it('usa formato compacto só a partir de 10 mil', () => {
    expect(compact(2500)).toBe('2.500');
    expect(compact(25000).replace(/\s/g, ' ')).toBe('25 mil'); // Intl usa espaço não separável
  });
});
