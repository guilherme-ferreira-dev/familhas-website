import { monthLabel, readHidden } from './dashboard-panel';

describe('painel da visão geral', () => {
  it('botão de olho lembra a escolha e nunca quebra sem storage', () => {
    expect(readHidden({ getItem: () => '1' })).toBe(true);
    expect(readHidden({ getItem: () => null })).toBe(false);
    expect(
      readHidden({
        getItem: () => {
          throw new Error('bloqueado');
        },
      }),
    ).toBe(false);
    expect(readHidden(null)).toBe(false);
  });

  it('rótulo do mês em português', () => {
    expect(monthLabel('2026-12')).toMatch(/dez/);
  });
});
