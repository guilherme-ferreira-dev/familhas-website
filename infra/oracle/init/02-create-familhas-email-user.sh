#!/bin/bash
# Cria o usuário/schema do familhas-email (ADR 0005), separado do schema da API.
# Roda só na primeira criação do volume do Oracle. A senha vem de variável de ambiente.
# Privilégio mínimo: só o que o Flyway do familhas-email precisa (sessão, tabelas e cota).
set -euo pipefail

EMAIL_USER="${EMAIL_DB_USERNAME:-FAMILHAS_EMAIL}"
EMAIL_PASSWORD="${EMAIL_DB_PASSWORD:-familhas_email123}"
PDB="${ORACLE_DATABASE:-FAMILHASPDB}"

sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = ${PDB};

DECLARE
  user_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(user_exists, -1920);
BEGIN
  EXECUTE IMMEDIATE 'CREATE USER ${EMAIL_USER} IDENTIFIED BY "${EMAIL_PASSWORD}"';
EXCEPTION
  WHEN user_exists THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE TO ${EMAIL_USER};
ALTER USER ${EMAIL_USER} DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
EXIT
SQL

echo "Usuario ${EMAIL_USER} pronto no ${PDB}"
