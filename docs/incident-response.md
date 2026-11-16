# Plano de resposta a incidentes

Para incidentes de segurança e de dados pessoais no CRM, na Loja e na infraestrutura. Registro em **CRM → Privacidade → Incidentes** (só admin; [ADR 0018](adr/0018-lgpd-operacao-e-entrega.md)). Não escreva dados pessoais no registro: só o que aconteceu, as categorias de dados e a estimativa de titulares.

## Papéis

| Papel | Quem | Faz |
|---|---|---|
| Responsável pelo incidente | admin do CRM de plantão | coordena, registra, decide contenção |
| Técnico | quem mantém a infraestrutura | contém, coleta evidências, corrige |
| Encarregado (DPO) | _a nomear_ | avalia risco aos titulares, comunica ANPD e titulares |
| Direção da agência | sócios | aprova comunicação externa |

## Fases

1. **Identificar.** Origens: alerta de login (bloqueio do Keycloak), auditoria (exportações, downloads, restaurações fora do padrão), relato da equipe ou de cliente, alerta do CI (gitleaks, Trivy), erro no health. Registre o incidente como `ABERTO` com a hora de detecção.
2. **Conter** (primeiras horas):
   - conta comprometida: Equipe → Desativar (encerra as sessões); se for admin, troque senha e TOTP no Keycloak;
   - segredo vazado: troque já (tabela abaixo) e reinicie a API;
   - link de documento enviado errado: links vencem em 5 min; se o arquivo vazou, registre;
   - servidor comprometido: isole da rede, preserve disco e logs antes de reinstalar.
   Atualize para `CONTIDO` com a hora.
3. **Registrar e preservar evidências.** Anote ou capture a auditoria do período (CRM → Auditoria, filtrada por data e pessoa; ou `GET /api/admin/audit-logs`), eventos de login do Keycloak (90 dias) e logs da API com o `X-Correlation-Id`. Não apague nada até o encerramento.
4. **Avaliar o risco aos titulares.** Houve dado pessoal? Quais categorias (CPF, documento, dado de criança)? Quantos titulares? Pode causar dano relevante (fraude, discriminação, exposição de menor)? Registre a conclusão em "Medidas tomadas".
5. **Remediar.** Corrija a causa (patch, permissão, configuração), restaure de backup se houve perda ([backup-recovery.md](backup-recovery.md)), confira que o problema não se repete.
6. **Comunicar.** Se houver risco ou dano relevante aos titulares, o encarregado comunica a **ANPD** e os **titulares** no prazo da regulamentação vigente da ANPD (Resolução CD/ANPD nº 15/2024: 3 dias úteis, a confirmar com o jurídico). A comunicação descreve a natureza dos dados, os titulares afetados, as medidas tomadas e os riscos. Registre as datas no incidente.
7. **Encerrar.** Lições aprendidas, ações preventivas, status `ENCERRADO` com a hora.

## Troca de segredos

| Segredo | Onde | Como trocar |
|---|---|---|
| `STORAGE_LINK_SECRET` | API | gere outro (32+ caracteres) e reinicie; links antigos deixam de valer |
| `KEYCLOAK_CRM_TEAM_SECRET` | Keycloak → Clients → familhas-crm-team → Credentials → Regenerate | atualize a variável e reinicie a API |
| `BACKUP_ENCRYPTION_KEY` | API | nova chave só vale para backups novos; guarde a antiga até os guardados vencerem (7 dias) |
| `SERPAPI_API_KEY`, `META_ACCESS_TOKEN`, chaves de IA | painel do fornecedor | revogue lá, gere outra, atualize e reinicie |
| SMTP (`MAIL_PASSWORD`) | provedor de e-mail | troque a senha de app |
| Senha do Oracle | banco | troque no banco e nas variáveis dos serviços |
| Admin do Keycloak | Keycloak master | troque e revise sessões |

## Contatos

_Pendência organizacional_: preencher com telefone do responsável, do técnico, do encarregado e o canal da ANPD (gov.br/anpd).
