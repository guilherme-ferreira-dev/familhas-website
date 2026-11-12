import { rememberReturnUrl, safeReturnUrl, takeReturnUrl } from './auth.service';

describe('safeReturnUrl', () => {
  it.each([
    ['/pacotes/1', '/pacotes/1'],
    ['/checkout/abc?x=1', '/checkout/abc?x=1'],
    ['https://evil.com', '/'],
    ['//evil.com/path', '/'],
    ['/\\evil.com', '/'],
    ['javascript:alert(1)', '/'],
    ['', '/'],
    [null, '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });
});

describe('returnUrl no storage', () => {
  it('guarda, devolve uma vez e apaga', () => {
    rememberReturnUrl('/busca?destino=1', sessionStorage);
    expect(takeReturnUrl(sessionStorage)).toBe('/busca?destino=1');
    expect(takeReturnUrl(sessionStorage)).toBeNull();
  });

  it('nunca devolve URL externa, mesmo se alguém gravar direto no storage', () => {
    sessionStorage.setItem('familhas.returnUrl', 'https://evil.com');
    expect(takeReturnUrl(sessionStorage)).toBe('/');
  });
});
