# Backup e restore do Oracle

Decisão em [ADR 0012](../adr/0012-qualidade-final.md) (D9). Os scripts ficam em `infra/oracle/` e usam o Data Pump (`expdp`/`impdp`) que já vem na imagem `gvenzl/oracle-free`.

## O que entra no backup

Um único dump, consistente no mesmo instante (`FLASHBACK_TIME`), com os três schemas do PDB `FAMILHASPDB`:

| Schema | Dono | Conteúdo |
|---|---|---|
| `FAMILHAS` | familhas-api | Catálogo, clientes, cotações, reservas, pagamentos, outbox, auditoria, histórico do Flyway |
| `FAMILHAS_EMAIL` | familhas-email | `processed_event` (idempotência) e histórico do Flyway |
| `KEYCLOAK` | Keycloak | Realm, usuários, senhas (hash), sessões e configuração dos clients |

Ficam de fora: mensagens em trânsito no RabbitMQ (o outbox reenvia o que não foi confirmado), e-mails do MailHog e o keystore do Keycloak (`infra/keycloak/keystore`, recriado sozinho no ambiente local).

## Backup

Pode rodar com tudo no ar.

```bash
infra/oracle/backup.sh              # grava em backups/ (ignorado pelo Git)
infra/oracle/backup.sh /caminho     # outra pasta
```

Saída: `familhas-AAAAMMDD-HHMMSS.dmp`, o log do export e o `.sha256`. O script falha se o export não terminar com "successfully completed".

Variáveis opcionais: `ORACLE_CONTAINER` (padrão `familhas-oracle`), `ORACLE_DATABASE` (`FAMILHASPDB`) e `BACKUP_SCHEMAS` (`FAMILHAS,FAMILHAS_EMAIL,KEYCLOAK`).

O dump tem dado pessoal de clientes e hash de senha do Keycloak. Guarde fora do repositório, com acesso restrito, e apague cópias antigas.

## Testar um backup (sem tocar nos dados)

```bash
infra/oracle/restore.sh --verify backups/familhas-20261115-182710.dmp
```

Confere o SHA-256, restaura em schemas temporários (`FAMILHAS_VERIFY`, `FAMILHAS_EMAIL_VERIFY`, `KEYCLOAK_VERIFY`), mostra a contagem de linhas das tabelas principais e apaga os temporários. Pode rodar com a stack no ar. Um backup que nunca foi restaurado não é backup: rode o `--verify` depois de cada backup importante.

## Restore

**Apaga os dados atuais** dos três schemas e recria a partir do dump.

1. Pare a `familhas-api` (Ctrl+C no `./mvnw spring-boot:run`).
2. Rode:

   ```bash
   infra/oracle/restore.sh backups/familhas-20261115-182710.dmp
   ```

   O script pede para digitar `RESTAURAR` (ou use `CONFIRM=yes`), para o Keycloak e o `familhas-email`, derruba sessões que sobrarem, apaga e recria os schemas (o dump traz usuários, senhas, privilégios e cotas) e sobe os dois serviços de novo.
3. Suba a API. O Flyway confere as migrations: se o dump for de antes de uma migration nova, ela é aplicada na subida.
4. Valide: `GET /actuator/health` = `UP`, login na Loja e no Admin, e a [checklist E2E](../e2e-checklist.md).

O `impdp` retorna código 5 quando termina com avisos (por exemplo, grant para um objeto que não existe); o script trata como sucesso. Qualquer outro código interrompe, e o log de import fica ao lado do dump.

## Restaurar só um schema

```bash
BACKUP_SCHEMAS=FAMILHAS_EMAIL infra/oracle/restore.sh backups/familhas-20261115-182710.dmp
```

Útil para voltar só a idempotência do `familhas-email`. Restaurar só `FAMILHAS` deixa os outros dois em outro instante no tempo: avalie antes (por exemplo, clientes criados no Keycloak depois do backup ficam sem `Client` local até o próximo login, que o recria).

## Volume perdido

Se o volume `familhas_oracle_data` sumir, `docker compose up -d --wait` recria o banco vazio e roda os scripts de `infra/oracle/init` (usuários `KEYCLOAK` e `FAMILHAS_EMAIL`). Depois rode o restore normalmente.

## Produção

O dump lógico atende o ambiente local, a cópia entre ambientes e a retenção de longo prazo. Em produção, some:

- backup físico (RMAN ou o backup gerenciado do provedor) com recuperação até um ponto no tempo (archivelog);
- cópia fora da região e criptografada;
- teste de restore agendado (o `--verify` serve de modelo);
- retenção definida junto com a política de LGPD (ADR 0012, D8).

## Validação

Feita em 15/11/2026 na stack local: backup de 6,4 MB em 48 s e duas restaurações seguidas em modo `--verify` (a segunda apagando os temporários da primeira), sem nenhum erro `ORA-`. Contagens restauradas iguais às do banco real (`CLIENTS` 4, `PROCESSED_EVENT` 11, `USER_ENTITY` 5).
