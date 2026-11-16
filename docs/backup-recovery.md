# Backup e recuperação

Dois backups diferentes, que não se substituem:

| | Backup operacional | Backup lógico do CRM |
|---|---|---|
| Para quê | recuperar o ambiente inteiro (desastre, servidor perdido) | devolver registros apagados por engano, pela tela |
| O que leva | schemas `FAMILHAS`, `FAMILHAS_EMAIL`, `KEYCLOAK` (Data Pump) **e** os arquivos do CRM (`.tar.gz`) | voos, pesquisas, vouchers e roteiros (JSON com hash) |
| Quem faz | quem opera a infraestrutura | admin/gestor na tela Backup |
| Restauração | apaga e recria tudo | só insere o que falta, nunca sobrescreve |
| Detalhes | [operacao/backup-restore.md](operacao/backup-restore.md) | [ADR 0017](adr/0017-administracao-do-crm.md) |

## Backup operacional

```bash
infra/oracle/backup.sh                    # dump + arquivos do CRM em backups/
infra/oracle/restore.sh --verify <dump>   # testa o dump em schemas temporários
```

Gera `familhas-AAAAMMDD-HHMMSS.dmp`, o log, `familhas-...-crm-files.tar.gz` (de `CRM_FILES_DIR`, padrão `backend/familhas-api/var/crm-files`) e um `.sha256` para cada.

Restaurar os arquivos do CRM (com a API parada):

```bash
shasum -a 256 -c backups/familhas-<data>-crm-files.tar.gz.sha256
mkdir -p "$STORAGE_LOCAL_ROOT" && tar -xzf backups/familhas-<data>-crm-files.tar.gz -C "$STORAGE_LOCAL_ROOT"
```

Restaure o dump e os arquivos **do mesmo backup**: arquivos mais novos que o banco ficam órfãos; mais velhos deixam registros sem documento.

## Proteção dos backups

- Têm CPFs, documentos e hash de senhas: pasta com acesso restrito, fora do repositório (`backups/` é ignorado), cópia fora do servidor e criptografada (ex.: `age` ou o cofre do provedor).
- Chaves separadas dos backups: `BACKUP_ENCRYPTION_KEY` e a chave da cópia externa ficam num cofre, não ao lado do arquivo.
- Retenção: _pendência organizacional_ (sugestão: diários por 7 dias, semanais por 4 semanas, mensais por 12 meses), alinhada com [lgpd.md](lgpd.md).

## RPO e RTO

**A definir com a agência.** Referência para a implantação atual (um servidor, backup por script):

| Cenário | RPO (perda máxima) | RTO (tempo para voltar) |
|---|---|---|
| Registro apagado por engano | último backup lógico/operacional | minutos (tela de restauração) |
| Banco corrompido | intervalo entre backups (ex.: 24 h com backup diário) | ~1 h (restore + validação) |
| Servidor perdido | intervalo entre cópias externas | horas (nova máquina, compose, restore) |

Para RPO de minutos em produção: backup físico com archivelog (RMAN ou serviço gerenciado), como já indica [operacao/backup-restore.md](operacao/backup-restore.md#produção).

## Alertas e métricas

- `GET /actuator/health` (público, só status) para o monitor de disponibilidade.
- Métricas Prometheus (`/actuator/prometheus`, token admin) — alertar em: health `DOWN`; `familhas_outbox_events{status="FAILED"}` > 0; `familhas_email_dlq_messages` > 0; erro 5xx acima do normal (`http_server_requests`); disco do volume de arquivos acima de 80%.
- Alertar se o backup diário não gerar arquivo novo ou se o `--verify` falhar (agendar os dois no cron do servidor).

## Teste de restauração

Mensal: `restore.sh --verify` no último dump e extração do `.tar.gz` numa pasta temporária, conferindo as contagens e alguns documentos. Anote data e resultado.

## Limitações

- Backups e `--verify` são manuais (ou cron no servidor); não há agendamento no projeto.
- O backup lógico do CRM não leva documentos, mídias, equipe nem auditoria.
