# Como contribuir

## Fluxo

1. Atualize a `main` e crie uma branch a partir dela. Nada vai direto na `main`.
2. Commits pequenos e atômicos, no padrão abaixo.
3. Abra um PR para a `main`. O CI (`.github/workflows/ci.yml`) roda build, testes, lint e contratos dos quatro projetos e precisa passar.
4. Merge com **merge commit** (preserva os commits da branch). Depois do merge, a branch é apagada.

## Branches

| Prefixo | Uso | Exemplo |
|---|---|---|
| `feat/fase-N-descricao` | Uma fase do guia | `feat/fase-11-qualidade-final` |
| `feat/descricao` | Funcionalidade fora de fase | `feat/estorno-pelo-admin` |
| `fix/descricao` | Correção | `fix/keycloak-admin-login` |
| `docs/descricao` | Só documentação | `docs/checklist-fase-10` |
| `chore/descricao` | Build, infra, dependências | `chore/compose-restart` |

Nomes em minúsculas, com hífen, sem acento.

## Commits: Conventional Commits

```
tipo(escopo): descrição curta no imperativo ou no particípio, em português, sem ponto final

Corpo opcional: o porquê da mudança, quebrado em ~100 colunas.
```

| Tipo | Quando |
|---|---|
| `feat` | Funcionalidade nova |
| `fix` | Correção de bug |
| `refactor` | Mudança de código sem mudar comportamento |
| `test` | Só testes |
| `docs` | Só documentação (README, ADR, checklist, contratos) |
| `ci` | Workflows do GitHub Actions |
| `chore` | Build, dependências, infra, tipos gerados |
| `perf` | Desempenho |

Escopos usados: `api`, `email`, `web`, `admin`, `infra`, `keycloak`, `checklist`, ou o domínio (`booking`, `payment`, `trip`, `observability`, `security`...).

Exemplos do histórico:

```
feat(admin): planilha de pagamentos em tempo real com exportacao
fix(keycloak): login do familhas-admin barrado para todos
test(trip): isolamento entre clientes em Oracle real por HTTP com JWT
docs(checklist): Fase 10 depois do merge e da subida local
```

Mudança que quebra contrato (API, evento ou migration incompatível): `feat(api)!: ...` e um parágrafo `BREAKING CHANGE:` no corpo.

## Antes de abrir o PR

- `./mvnw verify` em `backend/familhas-api` e `backend/familhas-email` (os testes com Testcontainers precisam do Docker).
- `npm run lint && npm run format:check && npm run test:ci && npm run build` em cada frontend alterado.
- Contrato HTTP mudou: atualize `docs/api/*.yaml`, gere os tipos (`npm run api:types`) e, se a rota estiver lá, a coleção em `docs/postman`.
- Decisão relevante: ADR curto em `docs/adr/` (contexto, decisão, consequências).
- Migration nova: próximo número livre (`V18__...`). Nunca altere uma migration já aplicada.
- Marque o que mudou em `docs/checklist.md`.
- Nenhum segredo, dado de cartão ou dado pessoal real em código, teste, log ou fixture.
