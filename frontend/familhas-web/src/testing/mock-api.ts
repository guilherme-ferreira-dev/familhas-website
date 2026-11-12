import { setupServer } from 'msw/node';
import { createHandlers, createMockState } from '../mocks/handlers';

/**
 * Servidor MSW para testes de tela: os mesmos handlers do build `mock`, sem latência.
 * Uso: `const api = useMockApi();` no topo do describe.
 */
export function useMockApi() {
  const server = setupServer();
  beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
  beforeEach(() => server.resetHandlers(...createHandlers(createMockState(), 0)));
  afterAll(() => server.close());
  return server;
}
