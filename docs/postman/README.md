# Coleção Postman

`familhas-api.postman_collection.json`: Loja (pública), área do cliente e Admin, para o ambiente local. Decisão em [ADR 0012](../adr/0012-qualidade-final.md) (D2).

## Importar

1. Postman > **Import** > selecione `docs/postman/familhas-api.postman_collection.json`.
2. Em **Settings > General**, desligue **SSL certificate verification**: o Keycloak local usa certificado self-signed (`https://localhost:8443`). Só no ambiente local.
3. As variáveis ficam na própria coleção (aba **Variables**): `baseUrl` (`http://localhost:8080`) e `keycloakUrl` (`https://localhost:8443`). Os ids (`packageId`, `quoteId`, `bookingId`, `clientId`...) são preenchidos pelas próprias requisições.

## Tokens

Os clients do Keycloak são públicos, com Authorization Code + PKCE. A coleção não guarda segredo nem senha.

| Pasta | Client | Usuário de teste |
|---|---|---|
| 2. Cliente | `familhas-web` | `user.familhas` / `user12345` |
| 3. Admin | `familhas-admin` | `admin.familhas` / `admin1234` |

Na pasta, aba **Authorization** > **Get New Access Token** > login na janela do Keycloak > **Use Token**. O Postman captura o redirect para `http://localhost:4200/` (ou `:4300/`) na própria janela; os frontends não precisam estar rodando. O token dura 5 minutos: gere outro quando a API responder 401.

## Ordem sugerida

1. **0. Saúde e documentação**: `Health` deve dar `UP`.
2. **1. Loja**: `Cidade de destino`, `Cidade de origem` e `Pacotes` guardam os ids usados depois.
3. **2. Cliente**: `Meu perfil` (cria o cliente e manda o e-mail de boas-vindas para o MailHog), `Criar cotação do pacote`, `Reservar a cotação`, `Abrir checkout` (precisa de `STRIPE_SECRET_KEY` de teste).
4. **3. Admin**: `Clientes` guarda um `clientId` para `Cotação manual`; `Métricas (Prometheus)` mostra as métricas da Familhas.

Toda requisição sai com um `X-Correlation-Id` novo (script da coleção). A API devolve o mesmo id na resposta: procure por ele no log da API e, se a requisição gerou evento, no log do `familhas-email`.

## Manutenção

`PostmanCollectionTests` (familhas-api) falha se uma requisição deixar de bater numa rota da API ou usar um query param que não está no contrato em `docs/api`. Ao mudar uma rota, atualize a coleção no Postman e exporte por cima deste arquivo (Collection v2.1).
