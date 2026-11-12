import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { APP_CONFIG, loadAppConfig } from './app/core/config/app-config';
import { environment } from './environments/environment';
import { startMockApi } from './mocks/start-mock-api';

async function start(): Promise<void> {
  await startMockApi();
  const config = await loadAppConfig(environment.configUrl);
  await bootstrapApplication(App, {
    ...appConfig,
    providers: [...appConfig.providers, { provide: APP_CONFIG, useValue: config }],
  });
}

start().catch((error: unknown) => {
  console.error(error);
  document.body.textContent =
    'Não foi possível abrir a loja agora. Tente de novo em instantes ou fale com a Familhas pelo WhatsApp.';
});
