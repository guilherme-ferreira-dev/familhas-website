#!/usr/bin/env bash
# Cria um membro da equipe no realm do CRM (ADR 0013, D5) pela Admin REST API do Keycloak.
#
# Senha temporária aleatória, mostrada uma única vez; no primeiro acesso o Keycloak exige trocar a senha
# e, para crm-admin, cadastrar o TOTP. Nenhuma senha fica em arquivo, variável exportada ou histórico.
#
# Uso:
#   KC_URL=https://sso.exemplo KC_CACERT=/caminho/ca.pem \
#     infra/keycloak/scripts/provision-crm-user.sh <usuario> <email> <nome> <sobrenome> <crm-admin|crm-gestor|crm-atendente|crm-marketing>
#
# Variáveis:
#   KC_URL          URL do Keycloak (padrão https://localhost:8443)
#   KC_CACERT       certificado da CA para validar o TLS (local: exporte o do keystore, ver infra/README.md)
#   KC_REALM        realm do CRM (padrão voe-familhas-crm)
#   KC_ADMIN_USER   usuário administrador do realm master (padrão: pergunta)
#   KC_ADMIN_PASSWORD  não use; a senha é pedida sem eco
set -euo pipefail

if [ "$#" -ne 5 ]; then
  sed -n '2,15p' "$0"
  exit 64
fi
USERNAME=$1 EMAIL=$2 FIRST=$3 LAST=$4 ROLE=$5
case "$ROLE" in
  crm-admin|crm-gestor|crm-atendente|crm-marketing) ;;
  *) echo "Papel inválido: $ROLE" >&2; exit 64 ;;
esac
for value in "$USERNAME" "$EMAIL" "$FIRST" "$LAST"; do
  if [[ "$value" =~ [\"\\\<\>] ]]; then echo "Caractere não permitido em: $value" >&2; exit 64; fi
done

KC_URL=${KC_URL:-https://localhost:8443}
KC_REALM=${KC_REALM:-voe-familhas-crm}
CURL=(curl -sS --fail-with-body)
if [ -n "${KC_CACERT:-}" ]; then CURL+=(--cacert "$KC_CACERT"); fi

if [ -z "${KC_ADMIN_USER:-}" ]; then read -r -p "Admin do Keycloak (realm master): " KC_ADMIN_USER; fi
read -r -s -p "Senha de $KC_ADMIN_USER: " ADMIN_PASSWORD; echo

TOKEN=$("${CURL[@]}" --data-urlencode grant_type=password --data-urlencode client_id=admin-cli \
  --data-urlencode "username=$KC_ADMIN_USER" --data-urlencode "password=$ADMIN_PASSWORD" \
  "$KC_URL/realms/master/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
unset ADMIN_PASSWORD
[ -n "$TOKEN" ] || { echo "Falha ao autenticar no Keycloak" >&2; exit 1; }
AUTH=(-H "Authorization: Bearer $TOKEN")
API="$KC_URL/admin/realms/$KC_REALM"

REQUIRED='"UPDATE_PASSWORD"'
if [ "$ROLE" = "crm-admin" ]; then REQUIRED='"UPDATE_PASSWORD","CONFIGURE_TOTP"'; fi
# Leitura finita do urandom: com pipefail, `tr </dev/urandom | head` morre de SIGPIPE.
TEMP_PASSWORD="$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9' | cut -c1-20)Aa1"

LOCATION=$("${CURL[@]}" -D - -o /dev/null "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$API/users" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[$REQUIRED],\"credentials\":[{\"type\":\"password\",\"value\":\"$TEMP_PASSWORD\",\"temporary\":true}]}" \
  | tr -d '\r' | sed -n 's/^[Ll]ocation: //p')
USER_ID=${LOCATION##*/}
[ -n "$USER_ID" ] || { echo "Usuário não criado" >&2; exit 1; }

ROLE_JSON=$("${CURL[@]}" "${AUTH[@]}" "$API/roles/$ROLE")
"${CURL[@]}" "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$API/users/$USER_ID/role-mappings/realm" \
  -d "[$ROLE_JSON]" >/dev/null

echo "Criado: $USERNAME ($ROLE) id=$USER_ID"
echo "Senha temporária (entregue por canal seguro; vale só para o primeiro acesso): $TEMP_PASSWORD"
