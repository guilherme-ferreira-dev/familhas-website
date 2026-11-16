# ADR 0017: Administração do CRM (Equipe, backup lógico e restauração)

- Status: aceito
- Data: 16/11/2026
- Etapa: 5 do prompt mestre do CRM (plano em [requirements-matrix.md](../requirements-matrix.md))

## Contexto

O JSON pede a gestão de membros e convites da equipe (somente administrador) e um backup único e centralizado: uma tela exporta e restaura todas as tabelas de negócio num arquivo, com envio por e-mail para `voefamilhas@gmail.com` e restauração "com validação e sem sobrescrever edições posteriores". O prompt troca "o primeiro cadastro vira admin" por provisionamento controlado no Keycloak (ADR 0013) e prefere, para o e-mail, um link temporário em vez do arquivo.

"Importar dados" continua bloqueado: sem amostras do sistema antigo, o formato seria chutado.

## Decisões

**D1. Equipe pela Admin API do Keycloak, com permissões mínimas.**
- Client confidencial `familhas-crm-team` com conta de serviço no realm do CRM. Ele só tem `view-users`, `query-users` e `manage-users` de `realm-management`, sem `view-realm`.
- Os papéis de cada pessoa são lidos dos mapeamentos do próprio usuário (`role-mappings/realm` e `/available`), nunca de `/roles/...`.
- Conferido num Keycloak 26.4 descartável: a conta de serviço lista e atribui papéis, mas recebe 403 em `/roles`, em clients e em outro realm.
- O segredo é gerado pelo Keycloak e fica só em `KEYCLOAK_CRM_TEAM_SECRET`. Sem ele, a Equipe aparece "não configurada" (503) na Visão geral.
- **Convite:** cria o usuário com o e-mail como login, atribui o papel e pede ao Keycloak o e-mail com o link de ações (definir senha, e cadastrar TOTP para admin), válido por 72 h. O retorno do link é o CRM.
- **Outras ações:** troca de papel (só papéis do CRM; vale no próximo login, porque as sessões são encerradas), desativar (encerra as sessões), reativar e reenviar o acesso.
- **Travas:** ninguém altera a própria conta (`SELF_CHANGE_NOT_ALLOWED`); o último admin ativo não perde o papel nem é desativado (`LAST_ADMIN`); usuário sem papel do CRM, como a própria conta de serviço, dá 404.
- **Permissões:** ver exige `crm:equipe:ler` (admin e gestor); alterar exige `criar`/`editar` (só admin). O menu e a tela seguem o "somente admin" do JSON.
- Todas as escritas vão para a auditoria (recurso `crm/team`).

**D2. Backup lógico versionado.**
- Um arquivo JSON (`voe-familhas-crm-backup`, versão 1) com voos de clientes (e passageiros), grupos e histórico de pesquisa de voos, vouchers e roteiros.
- `sha256` sobre as tabelas: arquivo alterado ou corrompido é recusado.
- **Fora do backup:** documentos e mídias (arquivos, como o JSON pede), posts do Instagram (dependem das mídias), logo, equipe (fica no Keycloak) e auditoria. Referências a arquivos (anexos, origem do voucher, fotos) não vão.
- O backup operacional completo continua sendo o Data Pump do Oracle (`infra/oracle/backup.sh`).
- **Baixar agora** (`crm:backup:exportar`, admin e gestor): o arquivo é gerado na hora e nada fica no servidor. Auditado como EXPORT.
- O arquivo tem CPFs: a tela avisa.

**D3. Envio por e-mail: link, nunca o arquivo.**
- O backup fica guardado criptografado (AES-256-GCM com `BACKUP_ENCRYPTION_KEY`, tabela `crm_backups`, V23) por 7 dias e depois é apagado sozinho.
- O e-mail (SMTP pelo `spring-boot-starter-mail`, destino padrão `voefamilhas@gmail.com`) leva só o link `/backup?baixar=<id>`. Para baixar é preciso login e permissão; o admin entra com TOTP.
- Sem SMTP ou sem chave, o envio fica desligado e o download direto continua.
- O health do SMTP fica fora do `/actuator/health`: uma queda do e-mail não derruba a API.

**D4. Restauração que nunca sobrescreve.**
- Duas fases: prévia (`apply=false`, roda tudo e desfaz) e restauração (`apply=true`), numa transação só.
- Cada registro do arquivo:
  - não existe → volta, com o id e as datas originais;
  - existe → fica como está, mesmo editado depois do backup;
  - outro registro usa o mesmo número de voucher, nome de grupo ou pesquisa → conflito, ignorado.
- Restaurar o mesmo arquivo de novo não duplica nada.
- As inserções são feitas por JDBC, com o UUID gravado como `RAW(16)` na ordem do Hibernate, para preservar ids e datas. Só o admin restaura (`crm:backup:restaurar`). Auditado como RESTORE.

## Consequências

- Restaurar não desfaz edições: para voltar um registro editado a uma versão antiga, a pessoa edita de novo. É o comportamento pedido ("sem sobrescrever edições posteriores").
- A tela de Equipe faz uma chamada por usuário para ler os papéis. Para a equipe de uma agência (dezenas de pessoas), é suficiente.
- Ambiente local: o client `familhas-crm-team` foi criado no realm existente igual ao JSON. O segredo se lê no console do Keycloak (Clients → familhas-crm-team → Credentials).

## Evidências (16/11/2026)

- `./mvnw verify`: 652 testes, 0 falhas, 0 pulados.
  - `CrmAdministrationIntegrationTests` (Oracle, 4 cenários): restauração devolve o voo e o voucher apagados sem sobrescrever o roteiro editado, prévia sem gravar, segunda restauração vazia; arquivo alterado ou de outro formato recusado; permissões por papel; e-mail só com o link e arquivo guardado criptografado; equipe por papel.
  - Unitários: `TeamServiceTests` (travas, troca de papel, convite), `KeycloakTeamDirectoryTests` (client credentials, 409, redirect do convite sem codificação dupla) e `BackupCipherTests`.
- Frontend: 98 testes, lint e build.
- Navegador real (Chrome, Keycloak real com TOTP, MailHog, schema Oracle descartável) — 16 de 17 cenários; o item que falhou foi conferido pela API:
  - admin entra com TOTP; a lista mostra só a equipe;
  - convite pelo e-mail do Keycloak com retorno para o CRM; a convidada define a senha e entra como atendente;
  - troca de papel e desativação (a desativada não entra mais);
  - backup baixado com hash; e-mail só com o link, que abre o backup guardado;
  - prévia e restauração do voo apagado com o mesmo id;
  - gestor baixa, mas não restaura nem acessa a Equipe.
- O E2E encontrou um defeito real, já corrigido e coberto por teste: o `redirect_uri` do convite saía codificado duas vezes.
