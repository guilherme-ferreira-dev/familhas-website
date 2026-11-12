import { setupWorker } from 'msw/browser';
import { createHandlers, createMockState, MockSnapshot } from './handlers';

const STORAGE_KEY = 'familhas.mockApi';

/**
 * Liga o MSW no navegador. Só é importado pelo build `mock` (fileReplacements).
 * O estado (cotações e reservas) fica no sessionStorage porque o "Stripe" do mock
 * recarrega a página, como o redirect real.
 */
export async function startMockApi(): Promise<void> {
  const state = createMockState(load(), (snapshot) => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
    } catch {
      // sem storage: o estado dura até recarregar a página
    }
  });
  const worker = setupWorker(...createHandlers(state));
  await worker.start({
    serviceWorker: { url: '/mockServiceWorker.js' },
    // Requisições sem handler (assets, Keycloak) seguem para a rede sem aviso.
    onUnhandledFrame: 'bypass',
    quiet: true,
  });
}

function load(): MockSnapshot | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as MockSnapshot) : null;
  } catch {
    return null;
  }
}
