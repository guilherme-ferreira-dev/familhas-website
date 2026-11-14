import { fakeToken } from '../../../testing/tokens';
import { realmRoles, rememberReturnUrl, safeReturnUrl, takeReturnUrl } from './auth.service';

describe('realmRoles', () => {
  it('lê realm_access.roles do access token', () => {
    expect(realmRoles(fakeToken(['ROLE_ADMIN', 'offline_access']))).toEqual([
      'ROLE_ADMIN',
      'offline_access',
    ]);
  });

  it('token ausente ou malformado não tem papel nenhum', () => {
    expect(realmRoles(null)).toEqual([]);
    expect(realmRoles('abc')).toEqual([]);
    expect(realmRoles('a.!!!.c')).toEqual([]);
  });
});

describe('safeReturnUrl', () => {
  it.each([
    ['/pagamentos', '/pagamentos'],
    ['/clientes/1?aba=ATIVOS', '/clientes/1?aba=ATIVOS'],
    ['https://evil.com', '/'],
    ['//evil.com/path', '/'],
    ['/\\evil.com', '/'],
    ['javascript:alert(1)', '/'],
    [null, '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });

  it('guarda a rota uma vez e nunca devolve URL externa', () => {
    rememberReturnUrl('/reservas', sessionStorage);
    expect(takeReturnUrl(sessionStorage)).toBe('/reservas');
    expect(takeReturnUrl(sessionStorage)).toBeNull();
    sessionStorage.setItem('familhas-admin.returnUrl', 'https://evil.com');
    expect(takeReturnUrl(sessionStorage)).toBe('/');
  });
});
