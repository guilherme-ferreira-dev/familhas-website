/**
 * Opções de BUILD (não de ambiente de execução). URLs, Keycloak e WhatsApp ficam em config.json.
 * `mocks` só é true no build `mock`, para o MSW nem entrar no bundle de produção.
 */
export const environment = {
  configUrl: 'config.json',
  mocks: false,
};
