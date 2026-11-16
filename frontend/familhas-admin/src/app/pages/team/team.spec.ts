import { pendingLabel, ROLE_LABEL } from './team.page';

describe('Equipe', () => {
  it('pendências do primeiro acesso em português', () => {
    expect(pendingLabel(['UPDATE_PASSWORD', 'CONFIGURE_TOTP'])).toBe(
      'Falta definir a senha e cadastrar o TOTP',
    );
    expect(pendingLabel([])).toBe('');
    expect(pendingLabel(['TERMS_AND_CONDITIONS'])).toBe('Falta terms_and_conditions');
  });

  it('os quatro papéis do CRM', () => {
    expect(Object.keys(ROLE_LABEL)).toEqual(['ADMIN', 'GESTOR', 'ATENDENTE', 'MARKETING']);
  });
});
