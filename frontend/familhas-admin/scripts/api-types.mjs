// Gera os tipos TypeScript dos contratos consumidos (npm run api:types):
// docs/api/admin.yaml (Fase 10) -> src/app/core/api/schema.d.ts
// docs/api/crm.yaml (CRM interno, ADR 0014) -> src/app/core/api/crm-schema.d.ts
import { execFileSync } from 'node:child_process';

const bin = (name) => new URL(`../node_modules/.bin/${name}`, import.meta.url).pathname;
const specs = [
  ['../../docs/api/admin.yaml', 'src/app/core/api/schema.d.ts'],
  ['../../docs/api/crm.yaml', 'src/app/core/api/crm-schema.d.ts'],
];

for (const [input, output] of specs) {
  execFileSync(bin('openapi-typescript'), [input, '-o', output], { stdio: 'inherit' });
  execFileSync(bin('prettier'), ['--write', output], { stdio: 'ignore' });
}
