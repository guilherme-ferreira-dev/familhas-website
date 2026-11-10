-- V7: Keycloak como fonte da verdade de identidade.
--
-- clients deixa de guardar senha (LGPD: não manter credencial que não é usada) e passa a
-- guardar o keycloak_id (claim sub, único). O vínculo pode ser criado depois: contas
-- administrativas cadastradas pelo Enéas (clientes atendidos por WhatsApp) ficam com
-- keycloak_id nulo até o primeiro login do cliente.

ALTER TABLE clients DROP COLUMN password;

ALTER TABLE clients ADD (
    keycloak_id VARCHAR2(64 CHAR));

-- Oracle permite vários NULL em índice UNIQUE, então contas sem login convivem normalmente.
ALTER TABLE clients ADD CONSTRAINT uk_clients_keycloak_id UNIQUE (keycloak_id);
