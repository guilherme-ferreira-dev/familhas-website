# Implantação

Hoje o projeto roda localmente (compose + `./mvnw` + `ng serve`). Este guia lista o que muda para homologação e produção. Ambientes **segregados**: banco, realm, segredos e storage próprios em cada um; nunca dados reais em teste sem controle.

## Componentes e exposição

| Componente | Exposto à internet? | Observação |
|---|---|---|
| `familhas-admin` (CRM) e `familhas-web` (Loja) | sim, estático por https | CDN ou Nginx; CSP de [seguranca/revisao-owasp.md](seguranca/revisao-owasp.md#csp-recomendada-para-os-frontends-hospedagem); `config.json` por ambiente |
| `familhas-api` | sim, só `/api` e `/actuator/health` | atrás de proxy https (`FORWARD_HEADERS_STRATEGY=framework`) |
| Keycloak | só `/realms/**` e `/resources/**` | console `/admin` por VPN ou IP permitido |
| Oracle, RabbitMQ, `familhas-email` | **não** | rede privada; firewall só para os serviços que usam |

## Perfil `prod` da API

`SPRINGDOC_ENABLED` desligado, logs JSON (`LOG_FORMAT=ecs`), health sem detalhes. Na subida, `ProductionReadinessCheck` derruba a API e lista o que falta se:

- `STORAGE_LINK_SECRET` tiver menos de 32 caracteres;
- `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CRM_ISSUER_URI` ou alguma origem de `CORS_ALLOWED_ORIGINS` não for https ou for localhost;
- Swagger estiver ligado;
- `BACKUP_ENCRYPTION_KEY` existir mas não tiver 32 bytes em Base64;
- `META_PUBLIC_BASE_URL` existir sem https.

## Variáveis

Lista completa em [.env.example](../.env.example). Obrigatórias em produção:

| Variável | Para quê |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | ativa o perfil acima |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | banco |
| `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CRM_ISSUER_URI`, `KEYCLOAK_URL` | validação de token e Admin API |
| `CORS_ALLOWED_ORIGINS`, `CRM_URL` | origens permitidas; links dos e-mails |
| `STORAGE_LOCAL_ROOT`, `STORAGE_LINK_SECRET` | volume privado dos arquivos do CRM e assinatura dos links |
| `KEYCLOAK_CRM_TEAM_SECRET` | tela Equipe |
| `BACKUP_ENCRYPTION_KEY`, `MAIL_*` | backup por e-mail (guarde a chave também fora do servidor) |

Opcionais (aparecem como "não configuradas"): `SERPAPI_API_KEY`, `META_*`, chaves de IA. IA de imagem: `AI_IMAGE_PROVIDER=openai` e `OPENAI_API_KEY` ([ADR 0019](adr/0019-ia-de-imagem-openai.md)). `PRIVACY_VOUCHER_RETENTION` (padrão `P5Y`) depende da decisão jurídica.

Gere segredos com `openssl rand -base64 32` e guarde num cofre (variáveis do provedor, Vault), nunca no repositório.

## Containers

- `familhas-email`: imagem multi-stage com usuário não-root (`backend/familhas-email/Dockerfile`).
- Compose local com `mem_limit` e healthchecks em todos os serviços.
- Volumes: `familhas_oracle_data` (banco) e o diretório de `STORAGE_LOCAL_ROOT` (arquivos), ambos privados e com backup ([backup-recovery.md](backup-recovery.md)). Criptografia em repouso pelo disco do provedor.

## Ordem de implantação

1. Backup operacional e `--verify` ([backup-recovery.md](backup-recovery.md)).
2. Keycloak (realm e clients; [keycloak.md](keycloak.md)).
3. API com `prod`: o Flyway aplica as migrations novas na subida. Confira `GET /actuator/health` = `UP`.
4. Frontends com o `config.json` do ambiente. O service worker do CRM atualiza no próximo carregamento.
5. Fumaça: login de cada papel, Visão geral com as integrações, um voo de teste num ambiente de homologação.

## Limitações atuais

- Não há pipeline de deploy: o CI constrói e testa; a publicação é manual.
- A API não tem imagem Docker própria (roda pelo `jar`); criar uma, não-root, como a do `familhas-email`, antes de orquestrar.
- Uma instância da API: jobs agendados (retenção, limpeza de backups, recuperação de publicações) não têm trava distribuída.
