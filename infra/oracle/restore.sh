#!/usr/bin/env bash
# Restore de um dump feito por infra/oracle/backup.sh (docs/operacao/backup-restore.md).
#
#   infra/oracle/restore.sh --verify backups/familhas-AAAAMMDD-HHMMSS.dmp
#       Restaura em schemas temporários (FAMILHAS_VERIFY, ...), mostra as contagens e apaga tudo.
#       Não toca nos schemas reais e pode rodar com a stack no ar. Use para testar se o backup presta.
#
#   infra/oracle/restore.sh backups/familhas-AAAAMMDD-HHMMSS.dmp
#       Restore de verdade: APAGA os schemas atuais e recria a partir do dump. Para o Keycloak e o
#       familhas-email antes e sobe de novo no fim. A familhas-api precisa estar parada (Ctrl+C no mvnw).
#       Pede confirmação; CONFIRM=yes pula a pergunta.
#
# Variáveis: ORACLE_CONTAINER (familhas-oracle), ORACLE_DATABASE (FAMILHASPDB),
#            BACKUP_SCHEMAS (FAMILHAS,FAMILHAS_EMAIL,KEYCLOAK)
set -euo pipefail

MODE=full
if [[ "${1:-}" == "--verify" ]]; then
  MODE=verify
  shift
fi
DUMP="${1:?Uso: infra/oracle/restore.sh [--verify] <arquivo.dmp>}"
[[ -f "$DUMP" ]] || { echo "Arquivo não encontrado: $DUMP" >&2; exit 1; }

CONTAINER="${ORACLE_CONTAINER:-familhas-oracle}"
PDB="${ORACLE_DATABASE:-FAMILHASPDB}"
SCHEMAS="${BACKUP_SCHEMAS:-FAMILHAS,FAMILHAS_EMAIL,KEYCLOAK}"
SUFFIX=""
[[ "$MODE" == verify ]] && SUFFIX="_VERIFY"
REMOTE_DIR=/opt/oracle/backup
NAME="$(basename "$DUMP" .dmp)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "${DUMP}.sha256" ]]; then
  ( cd "$(dirname "$DUMP")" && shasum -a 256 -c "$(basename "$DUMP").sha256" )
fi

REMAP=""
TARGETS=""
IFS=',' read -ra LIST <<< "$SCHEMAS"
for schema in "${LIST[@]}"; do
  TARGETS+="${TARGETS:+,}${schema}${SUFFIX}"
  [[ -n "$SUFFIX" ]] && REMAP+="${REMAP:+,}${schema}:${schema}${SUFFIX}"
done

if [[ "$MODE" == full ]]; then
  echo "ATENÇÃO: os schemas ${SCHEMAS} de ${PDB} serão APAGADOS e recriados a partir de ${DUMP}."
  echo "Pare a familhas-api antes. Keycloak e familhas-email serão parados e iniciados de novo."
  if [[ "${CONFIRM:-}" != "yes" ]]; then
    read -r -p "Digite RESTAURAR para continuar: " answer
    [[ "$answer" == "RESTAURAR" ]] || { echo "Cancelado."; exit 1; }
  fi
  docker compose -f "$ROOT/compose.yaml" stop keycloak familhas-email
fi

docker exec "$CONTAINER" mkdir -p "$REMOTE_DIR"
# Cópia por stream, não docker cp: assim o arquivo fica com o usuário oracle, que o impdp consegue ler.
docker exec -i "$CONTAINER" bash -c "cat > '${REMOTE_DIR}/${NAME}.dmp'" < "$DUMP"

# Derruba sessões e apaga os schemas de destino (se existirem), depois importa. O dump traz usuários, senhas,
# privilégios e cotas, então o impdp recria tudo.
docker exec -i "$CONTAINER" bash -s -- "$PDB" "$REMOTE_DIR" "$TARGETS" <<'SH'
set -euo pipefail
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = $1;
CREATE OR REPLACE DIRECTORY familhas_backup AS '$2';
DECLARE
  targets SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST($(echo "$3" | sed "s/[^,]*/'&'/g"));
BEGIN
  FOR i IN 1 .. targets.COUNT LOOP
    FOR s IN (SELECT sid, serial# AS serial FROM v\$session WHERE username = targets(i)) LOOP
      EXECUTE IMMEDIATE 'ALTER SYSTEM KILL SESSION ''' || s.sid || ',' || s.serial || ''' IMMEDIATE';
    END LOOP;
    FOR u IN (SELECT username FROM dba_users WHERE username = targets(i)) LOOP
      EXECUTE IMMEDIATE 'DROP USER ' || u.username || ' CASCADE';
    END LOOP;
  END LOOP;
END;
/
EXIT
SQL
SH

echo "Importando ${TARGETS}..."
set +e
docker exec -i "$CONTAINER" bash -s -- "$PDB" "$SCHEMAS" "$NAME" "$REMAP" <<'SH'
impdp_remap=""
[[ -n "$4" ]] && impdp_remap="REMAP_SCHEMA=$4"
impdp "system/\"${ORACLE_PASSWORD}\"@//localhost:1521/$1" \
  SCHEMAS="$2" DIRECTORY=familhas_backup DUMPFILE="$3.dmp" LOGFILE="$3-import.log" $impdp_remap
SH
status=$?
set -e
docker cp "${CONTAINER}:${REMOTE_DIR}/${NAME}-import.log" "$(dirname "$DUMP")/${NAME}-import$(echo "$SUFFIX" | tr "[:upper:]" "[:lower:]").log" || true

echo "Linhas por tabela restaurada:"
docker exec -i "$CONTAINER" bash -s -- "$PDB" "$TARGETS" <<'SH'
sqlplus -s / as sysdba <<SQL
SET PAGESIZE 200 LINESIZE 120 FEEDBACK OFF
ALTER SESSION SET CONTAINER = $1;
COLUMN owner FORMAT A24
COLUMN table_name FORMAT A40
SELECT owner, table_name,
       TO_NUMBER(EXTRACTVALUE(XMLTYPE(DBMS_XMLGEN.GETXML(
         'SELECT COUNT(*) c FROM "' || owner || '"."' || table_name || '"')), '/ROWSET/ROW/C')) AS linhas
  FROM dba_tables
 WHERE owner IN ($(echo "$2" | sed "s/[^,]*/'&'/g"))
   AND table_name IN ('FLYWAY_SCHEMA_HISTORY', 'CLIENTS', 'BOOKINGS', 'PAYMENTS', 'OUTBOX_EVENT',
                      'PROCESSED_EVENT', 'USER_ENTITY', 'REALM')
 ORDER BY owner, table_name;
EXIT
SQL
SH

docker exec "$CONTAINER" rm -f "${REMOTE_DIR}/${NAME}.dmp" "${REMOTE_DIR}/${NAME}-import.log"

if [[ "$MODE" == verify ]]; then
  docker exec -i "$CONTAINER" bash -s -- "$PDB" "$TARGETS" <<'SH'
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = $1;
BEGIN
  FOR u IN (SELECT username FROM dba_users WHERE username IN ($(echo "$2" | sed "s/[^,]*/'&'/g"))) LOOP
    EXECUTE IMMEDIATE 'DROP USER ' || u.username || ' CASCADE';
  END LOOP;
END;
/
EXIT
SQL
SH
  echo "Verificação concluída e schemas ${TARGETS} apagados."
else
  docker compose -f "$ROOT/compose.yaml" start keycloak familhas-email
  echo "Restore concluído. Suba a familhas-api de novo (o Flyway valida as migrations)."
fi

# impdp retorna 5 quando termina com avisos (ex.: grant a objeto que não existe); erro real é outro código.
if [[ $status -ne 0 && $status -ne 5 ]]; then
  echo "impdp falhou (código $status): veja o log de importação." >&2
  exit $status
fi
