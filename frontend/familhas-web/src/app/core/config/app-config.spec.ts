import { InvalidAppConfigError, loadAppConfig, parseAppConfig } from './app-config';

const valid = {
  apiBaseUrl: 'http://localhost:8080/',
  siteUrl: 'http://localhost:4200',
  oidc: {
    authority: 'https://localhost:8443/realms/familhas',
    clientId: 'familhas-web',
    scope: 'openid profile email',
  },
  whatsapp: { phone: '', defaultMessage: 'Olá! Vim pelo site da Familhas.' },
};

describe('parseAppConfig', () => {
  it('aceita a configuração local e remove a barra final das URLs', () => {
    const config = parseAppConfig(valid);
    expect(config.apiBaseUrl).toBe('http://localhost:8080');
    expect(config.whatsapp.phone).toBe('');
  });

  it('aceita telefone só com dígitos', () => {
    const config = parseAppConfig({
      ...valid,
      whatsapp: { ...valid.whatsapp, phone: '5561999999999' },
    });
    expect(config.whatsapp.phone).toBe('5561999999999');
  });

  it.each(['+55 61 99999-9999', '123', 'abc'])(
    'recusa telefone fora do formato wa.me: %s',
    (phone) => {
      expect(() => parseAppConfig({ ...valid, whatsapp: { ...valid.whatsapp, phone } })).toThrow(
        InvalidAppConfigError,
      );
    },
  );

  it('lista todos os campos faltando', () => {
    expect(() => parseAppConfig({})).toThrow(/apiBaseUrl.*siteUrl.*oidc.authority/);
  });

  it('recusa URL que não é http(s)', () => {
    expect(() => parseAppConfig({ ...valid, apiBaseUrl: 'javascript:alert(1)' })).toThrow(
      /apiBaseUrl deve ser uma URL/,
    );
  });
});

describe('loadAppConfig', () => {
  it('falha com mensagem clara quando o arquivo não existe', async () => {
    const fetchFn = (() => Promise.resolve(new Response('', { status: 404 }))) as typeof fetch;
    await expect(loadAppConfig('config.json', fetchFn)).rejects.toThrow(/HTTP 404/);
  });

  it('carrega e valida o arquivo', async () => {
    const fetchFn = (() => Promise.resolve(Response.json(valid))) as typeof fetch;
    await expect(loadAppConfig('config.json', fetchFn)).resolves.toMatchObject({
      oidc: { clientId: 'familhas-web' },
    });
  });
});
