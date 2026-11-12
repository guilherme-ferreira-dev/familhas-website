/**
 * Opções de BUILD (não de ambiente de execução). URLs, Keycloak e WhatsApp ficam em config.json.
 * No build `mock` este arquivo é trocado por environment.mock.ts.
 */
export const environment = {
  configUrl: 'config.json',
  mocks: false,
};
