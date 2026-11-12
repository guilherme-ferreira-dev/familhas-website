# ADR 0009: Frontend da Loja (familhas-web)

- Status: aceito
- Data: 12/11/2026
- Fase: 7

## Contexto

A Fase 7 pede a Loja em Angular 21+: login e logout com OIDC + PKCE, interceptor JWT, Home, busca com filtros, detalhe do pacote, checkout, tratamento de erros, layout mobile-first, SEO básico, acessibilidade e o botão flutuante de WhatsApp com número por ambiente (guia, seção 8).

A Fase 6 (reserva e pagamento) roda em paralelo, em outra branch. Por isso a Loja precisava de um contrato e de mocks para avançar sem backend. Na primeira resposta da fase, o responsável decidiu:
- escrever o contrato agora (opção "a");
- usar o Stripe Checkout hospedado;
- permitir mudanças fora de `frontend/familhas-web` (`docs/`, CI e README);
- usar um número placeholder para o WhatsApp.

## Decisões

**D1. Angular 22, standalone, zoneless e signals.** É a versão estável na data (22.2.6). A 22.2.7 saiu no mesmo dia, com pacotes incompletos no npm. As versões do Angular ficam fixas, sem `^`. A suíte de testes usa Vitest, o padrão do CLI, com jsdom. O lint usa angular-eslint, incluindo as regras de acessibilidade de template. A versão do Node fica no `.nvmrc` (24 LTS).

**D2. Contratos em `docs/api`, um arquivo por fase.**
- `familhas-api.yaml` (Fase 7): catálogo, busca e `/api/me`, escrito a partir dos DTOs reais da Fase 5.
- `checkout.yaml` (Fase 6): cotações, reservas, checkout e webhook. É de responsabilidade da Fase 6 e é combinado com ela.
- Os tipos TypeScript são gerados dos dois arquivos (`npm run api:types`, com openapi-typescript) e versionados. O CI falha se ficarem desatualizados (`api:check`).
- Enquanto a Fase 6 não estiver na branch, o `checkout-schema.d.ts` gerado a partir de `feat/fase-6-stripe` (commit `bba79e1`) é mantido como está.

**D3. Configuração em runtime (`config.json`), validada antes do bootstrap.**
- O arquivo carrega URL da API, URL pública, issuer e client do Keycloak e o número e a saudação do WhatsApp.
- O mesmo build serve qualquer ambiente; só o arquivo muda no deploy. Ele é público: nunca guarda segredos.
- Já as opções de build (mocks ligados ou não) ficam em `environment*.ts`.

**D4. Mocks com MSW, só no build `mock`.**
- Os handlers seguem os dois contratos e são tipados pelos tipos gerados: se o contrato mudar, o build quebra.
- Eles simulam os `code` de erro do checkout e o webhook (a reserva confirma na segunda consulta).
- MSW e login simulado entram por `fileReplacements`, então não vão para o bundle de produção.
- Os mesmos handlers rodam nos testes de tela (`msw/node`).

**D5. OIDC com angular-auth-oidc-client atrás de uma interface `AuthService`.**
- A biblioteca é certificada pela OpenID Foundation e usa Authorization Code + PKCE (S256).
- A renovação é por refresh token, sem iframe, porque os navegadores bloqueiam cookies de terceiros.
- O interceptor da biblioteca fica desligado. O nosso `jwtInterceptor` só envia o token para `{apiBaseUrl}/api/`.
- A rota de volta depois do login é guardada no `sessionStorage` e só aceita caminhos internos (contra open redirect).
- Keycloak fora do ar não derruba a Loja: o catálogo é público.

**D6. Erros pelo `code`, nunca pelo `detail`.**
- O Problem Details vira `ApiError` (kind, status, code, errors e extras).
- As mensagens em português saem do `code` estável do `checkout.yaml` ou do tipo de erro. O `detail` técnico não vai para a tela.
- Um 401 da API manda para o login e volta para a mesma tela.

**D7. Checkout hospedado isolado em `PaymentRedirect`.**
- O redirect só aceita `https://checkout.stripe.com`. No build mock, aceita também a própria origem.
- Na volta, a tela consulta a reserva a cada 3 s por 2 min e depois a cada 15 s, até sair de `PENDING_PAYMENT`. A volta não confirma nada: quem confirma é o webhook, e o Pix é assíncrono.
- Trocar pelo Payment Element muda só essa peça.

**D8. WhatsApp com contexto e sem dados pessoais.**
- O link `wa.me` usa o número do `config.json`; com o número vazio, o botão some.
- A mensagem cita o pacote (nome e link), a cotação ou a reserva (8 primeiros caracteres do id e total), nunca nome, e-mail ou documento do cliente (LGPD).
- O placeholder `5500000000000` usa um DDD que não existe, então ninguém real recebe mensagem até o número oficial ser configurado.

**D9. SEO básico em SPA.**
- Cada rota define título, description, robots, canonical e Open Graph a partir do `siteUrl`.
- O detalhe do pacote usa o nome do pacote.
- Cotação, checkout e 404 ficam com `noindex`, e o `robots.txt` bloqueia as rotas do cliente.
- SSR e prerender ficam para quando houver domínio e hospedagem definidos.

**D10. Acessibilidade básica.**
- Link para pular ao conteúdo e foco no `main` a cada navegação.
- Landmarks, `aria-live` nos resultados e avisos, e erros de campo com `aria-invalid` e `aria-describedby`.
- Alvos de toque de 44 px, contraste AA e respeito a `prefers-reduced-motion`.
- Validado com axe-core no navegador nas telas do fluxo principal.

## Consequências

- A Loja roda inteira sem backend (`npm run start:mock`), e as Fases 6 e 7 evoluem em paralelo sem conflito de arquivo em `docs/api`.
- Mudança incompatível no `checkout.yaml` quebra o build da Loja depois de `npm run api:types`. Isso é intencional.
- O login real depende de o navegador confiar no certificado self-signed do Keycloak local (`https://localhost:8443`).
- O preço de vitrine e o total da cotação podem divergir (ADR 0006, D2). A tela deixa claro que o preço final sai na cotação.
- Ficaram fora: Minhas viagens e recomendação (Fase 8), chat (Fase 9), cotação de itens avulsos da busca e SSR.
