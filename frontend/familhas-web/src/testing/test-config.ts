import { Provider } from '@angular/core';
import { APP_CONFIG, AppConfig } from '../app/core/config/app-config';
import { provideLocale } from '../app/core/locale';

export const TEST_API = 'http://api.test';

export const testConfig: AppConfig = {
  apiBaseUrl: TEST_API,
  siteUrl: 'http://localhost:4200',
  oidc: { authority: 'https://kc.test/realms/familhas', clientId: 'familhas-web', scope: 'openid' },
  whatsapp: { phone: '5500000000000', defaultMessage: 'Olá!' },
};

/** APP_CONFIG de teste + locale pt-BR (igual ao app). */
export function provideTestConfig(overrides: Partial<AppConfig> = {}): Provider[] {
  return [{ provide: APP_CONFIG, useValue: { ...testConfig, ...overrides } }, ...provideLocale()];
}
