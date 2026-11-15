#!/usr/bin/env bash
# Backup lógico do Oracle local com Data Pump (guia, Fase 11; docs/operacao/backup-restore.md).
#
# Exporta os schemas da API, do familhas-email e do Keycloak num único dump consistente (FLASHBACK_TIME) e copia
# para backups/ na raiz (ignorado pelo Git). Pode rodar com tudo no ar.
#
#   infra/oracle/backup.sh [pasta-de-destino]
#
# Variáveis: ORACLE_CONTAINER (familhas-oracle), ORACLE_DATABASE (FAMILHASPDB),
#            BACKUP_SCHEMAS (FAMILHAS,FAMILHAS_EMAIL,KEYCLOAK)
set -euo pipefail

CONTAINER="${ORACLE_CONTAINER:-familhas-oracle}"
PDB="${ORACLE_DATABASE:-FAMILHASPDB}"
SCHEMAS="${BACKUP_SCHEMAS:-FAMILHAS,FAMILHAS_EMAIL,KEYCLOAK}"
OUT_DIR="${1:-backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
NAME="familhas-${STAMP}"
REMOTE_DIR=/opt/oracle/backup

mkdir -p "$OUT_DIR"

# Diretório do Data Pump dentro do container. A senha do SYSTEM é a ORACLE_PASSWORD do próprio container:
# nunca passa pela linha de comando do host.
docker exec -i "$CONTAINER" bash -s -- "$PDB" "$REMOTE_DIR" <<'SH'
set -euo pipefail
mkdir -p "$2"
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = $1;
CREATE OR REPLACE DIRECTORY familhas_backup AS '$2';
EXIT
SQL
SH

echo "Exportando ${SCHEMAS} de ${PDB}..."
docker exec -i "$CONTAINER" bash -s -- "$PDB" "$SCHEMAS" "$NAME" <<'SH'
set -euo pipefail
expdp "system/\"${ORACLE_PASSWORD}\"@//localhost:1521/$1" \
  SCHEMAS="$2" DIRECTORY=familhas_backup DUMPFILE="$3.dmp" LOGFILE="$3-export.log" \
  FLASHBACK_TIME=SYSTIMESTAMP REUSE_DUMPFILES=YES
SH

docker cp "${CONTAINER}:${REMOTE_DIR}/${NAME}.dmp" "${OUT_DIR}/${NAME}.dmp"
docker cp "${CONTAINER}:${REMOTE_DIR}/${NAME}-export.log" "${OUT_DIR}/${NAME}-export.log"
docker exec "$CONTAINER" rm -f "${REMOTE_DIR}/${NAME}.dmp" "${REMOTE_DIR}/${NAME}-export.log"

if ! grep -q "successfully completed" "${OUT_DIR}/${NAME}-export.log"; then
  echo "Export terminou com avisos ou erros: veja ${OUT_DIR}/${NAME}-export.log" >&2
  exit 1
fi

( cd "$OUT_DIR" && shasum -a 256 "${NAME}.dmp" > "${NAME}.dmp.sha256" )
echo "Backup pronto: ${OUT_DIR}/${NAME}.dmp ($(du -h "${OUT_DIR}/${NAME}.dmp" | cut -f1))"
