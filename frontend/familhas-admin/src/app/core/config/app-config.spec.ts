import { InvalidAppConfigError, parseAppConfig } from './app-config';

describe('parseAppConfig', () => {
  it('normaliza as URLs', () => {
    expect(
      parseAppConfig({
        apiBaseUrl: 'http://localhost:8080/',
        oidc: {
          authority: 'https://kc/realms/familhas/',
          clientId: 'familhas-admin',
          scope: 'openid',
        },
      }),
    ).toEqual({
      apiBaseUrl: 'http://localhost:8080',
      oidc: {
        authority: 'https://kc/realms/familhas',
        clientId: 'familhas-admin',
        scope: 'openid',
      },
    });
  });

  it('lista todos os problemas', () => {
    expect(() => parseAppConfig({ apiBaseUrl: 'ftp://x', oidc: {} })).toThrow(
      InvalidAppConfigError,
    );
  });
});
