import { fromLocalInput, toLocalInput } from './privacy.page';

describe('PrivacyPage datas', () => {
  it('ida e volta entre ISO e datetime-local preserva o instante (sem segundos)', () => {
    const iso = '2026-11-16T13:45:00.000Z';
    expect(fromLocalInput(toLocalInput(iso))).toBe(iso);
  });

  it('vazio vira null', () => {
    expect(toLocalInput(null)).toBe('');
    expect(fromLocalInput('')).toBeNull();
  });
});
