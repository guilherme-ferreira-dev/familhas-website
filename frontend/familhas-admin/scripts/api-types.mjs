// Gera os tipos TypeScript do contrato do Admin (npm run api:types):
// docs/api/admin.yaml (Fase 10) -> src/app/core/api/schema.d.ts
import { execFileSync } from 'node:child_process';

const bin = (name) => new URL(`../node_modules/.bin/${name}`, import.meta.url).pathname;
const input = '../../docs/api/admin.yaml';
const output = 'src/app/core/api/schema.d.ts';

execFileSync(bin('openapi-typescript'), [input, '-o', output], { stdio: 'inherit' });
execFileSync(bin('prettier'), ['--write', output], { stdio: 'ignore' });
