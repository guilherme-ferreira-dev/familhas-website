// Gera os tipos TypeScript dos contratos em docs/api (npm run api:types).
// - familhas-api.yaml (Fase 7): catálogo, busca e /api/me -> src/app/core/api/schema.d.ts
// - checkout.yaml (Fase 6): cotações, reservas e checkout -> src/app/core/api/checkout-schema.d.ts
// - minhas-viagens.yaml (Fase 8): viagens, favoritos, avaliações e recomendação -> src/app/core/api/trips-schema.d.ts
// Enquanto a Fase 6 não estiver na branch, checkout.yaml pode não existir: nesse caso o arquivo gerado
// já versionado é mantido (passe CHECKOUT_SPEC=<caminho> para gerar a partir de outra cópia).
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';

const bin = (name) => new URL(`../node_modules/.bin/${name}`, import.meta.url).pathname;
const specs = [
  { input: '../../docs/api/familhas-api.yaml', output: 'src/app/core/api/schema.d.ts', required: true },
  {
    input: process.env.CHECKOUT_SPEC ?? '../../docs/api/checkout.yaml',
    output: 'src/app/core/api/checkout-schema.d.ts',
    required: false,
  },
  {
    input: '../../docs/api/minhas-viagens.yaml',
    output: 'src/app/core/api/trips-schema.d.ts',
    required: true,
  },
];

for (const { input, output, required } of specs) {
  if (!existsSync(input)) {
    if (required) throw new Error(`Contrato não encontrado: ${input}`);
    console.warn(`Aviso: ${input} não existe nesta branch; mantendo ${output} versionado.`);
    continue;
  }
  execFileSync(bin('openapi-typescript'), [input, '-o', output], { stdio: 'inherit' });
  execFileSync(bin('prettier'), ['--write', output], { stdio: 'ignore' });
}
