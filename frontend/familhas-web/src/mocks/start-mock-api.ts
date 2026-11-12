/**
 * Build normal: não liga mocks. O build `mock` troca este arquivo por start-mock-api.mock.ts
 * (fileReplacements no angular.json), então o MSW nunca entra no bundle de produção.
 */
export async function startMockApi(): Promise<void> {
  // nada a fazer fora do build mock
}
