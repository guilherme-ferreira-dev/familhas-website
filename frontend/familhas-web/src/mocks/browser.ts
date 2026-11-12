import { setupWorker } from 'msw/browser';
import { createHandlers } from './handlers';

/** Liga o MSW no navegador. Só é importado pelo build `mock` (environment.mocks). */
export async function startMockApi(): Promise<void> {
  const worker = setupWorker(...createHandlers());
  await worker.start({
    serviceWorker: { url: '/mockServiceWorker.js' },
    // Requisições sem handler (assets, Keycloak) seguem para a rede sem aviso.
    onUnhandledFrame: 'bypass',
    quiet: true,
  });
}
