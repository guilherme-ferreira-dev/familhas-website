# Oracle e Flyway

Oracle Free 23 (`gvenzl/oracle-free:23-slim`), PDB `FAMILHASPDB`. Decisões de ids em [ADR 0002](adr/0002-ids-uuid-raw16.md).

## Schemas

| Schema | Dono | Migrations |
|---|---|---|
| `FAMILHAS` | familhas-api | `backend/familhas-api/src/main/resources/db/migration` |
| `FAMILHAS_EMAIL` | familhas-email | `backend/familhas-email/src/main/resources/db/migration` |
| `KEYCLOAK` | Keycloak | gerenciado pelo próprio Keycloak |

Os usuários `KEYCLOAK` e `FAMILHAS_EMAIL` são criados por `infra/oracle/init` na primeira subida do volume.

## Regras

- **Só migrations novas.** Nunca edite uma migration já aplicada em algum ambiente compartilhado; o Flyway valida o checksum e a API não sobe.
- Numeração sequencial (`V25__descricao.sql`), nome em inglês, comentário no topo dizendo a etapa e o ADR.
- Ids `RAW(16)` (UUID); datas de negócio `TIMESTAMP(6) WITH TIME ZONE` em UTC, horários de bilhete `TIMESTAMP` sem fuso (ADR 0015).
- Booleanos novos: `BOOLEAN` nativo do Oracle 23 (o Hibernate valida o tipo; `NUMBER(1)` falha na validação).
- Toda FK com índice; `CHECK` para enums; JSON com `IS JSON`.
- A API sobe com `ddl-auto=validate`: diferença entre entidade e tabela derruba a subida (e o teste de integração).
- Dados legados: migração com script reversível ou estratégia documentada, e backup testado antes ([backup-recovery.md](backup-recovery.md)).

## Histórico

| Versões | Conteúdo |
|---|---|
| V1–V17 | Loja: catálogo, ids UUID, Keycloak, outbox, cotação, reserva, pagamento, auditoria, margem, retenção |
| V18 | CRM: arquivos e ações de auditoria |
| V19–V21 | Voos de clientes, pesquisa de voos, vouchers |
| V22 | Marketing: logo, posts do Instagram, roteiros |
| V23 | Backups guardados criptografados |
| V24 | Registro de incidentes (LGPD) |

## Testes

Os testes de integração sobem um Oracle descartável com Testcontainers (`gvenzl/oracle-free:23-slim-faststart`) e aplicam todas as migrations do zero. São pulados sem Docker. Com pouca memória no Docker, pare o Keycloak e o `familhas-email` antes (`docker compose stop keycloak familhas-email`).
