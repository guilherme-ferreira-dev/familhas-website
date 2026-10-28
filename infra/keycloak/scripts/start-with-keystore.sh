#!/bin/sh
set -eu

KEYSTORE_PATH="${KC_HTTPS_KEY_STORE_FILE:-/opt/keycloak/keystore/keystore.p12}"
KEYSTORE_PASSWORD="${KC_HTTPS_KEY_STORE_PASSWORD:-changeit}"
KEYSTORE_TYPE="${KC_HTTPS_KEY_STORE_TYPE:-PKCS12}"

if [ ! -f "$KEYSTORE_PATH" ]; then
  echo "Keystore nao encontrado em $KEYSTORE_PATH. Gerando certificado self-signed para ambiente local..."
  mkdir -p "$(dirname "$KEYSTORE_PATH")"
  keytool -genkeypair \
    -storetype "$KEYSTORE_TYPE" \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -alias keycloak-local \
    -keyalg RSA \
    -keysize 2048 \
    -validity 3650 \
    -dname "CN=localhost, OU=Familhas, O=Familhas, L=Local, S=Local, C=BR"
fi

# O build (KC_DB, health) já foi feito na imagem; ver infra/keycloak/Dockerfile.
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm --hostname-strict=false
