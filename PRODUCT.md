# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Famílias brasileiras planejando uma viagem, muitas vezes pelo celular, comparando pacotes, pedindo cotação e fechando a compra. (Confirmado em entrevista.)

## Product Purpose
A Loja da Familhas, agência de viagens, permite buscar voos e pacotes, ver o detalhe, pedir cotação, reservar e pagar (Stripe Checkout hospedado) e falar com a agência por WhatsApp em todas as telas. Sucesso: a família chega à reserva paga, ou à conversa no WhatsApp, sem se perder.

## Positioning
Atendimento humano: agência pequena, com o Enéas atendendo pessoalmente pelo WhatsApp antes e depois da compra. (Confirmado em entrevista.)

## Operating Context
Angular 22 standalone, zoneless, signals, em `frontend/familhas-web`. Login OIDC + PKCE (Keycloak), pagamento em Stripe hospedado (Pix é assíncrono, a reserva confirma por webhook), mocks com MSW (`npm run start:mock`). Decisões no ADR 0009.

## Capabilities and Constraints
- Telas: home, busca com filtros, detalhe do pacote, cotação, checkout e retorno do checkout, 404; cabeçalho, rodapé e botão flutuante de WhatsApp.
- Mobile-first, acessibilidade (lint de template), SEO básico, textos em português do Brasil.
- Mensagens de erro saem do `code` da API, nunca do `detail`.
- WhatsApp sem dados pessoais (LGPD); número em `public/config.json` (hoje placeholder).
- Tokens atuais em `src/styles.scss` (contraste AA); o redesign pode substituí-los.

## Brand Commitments
Nome "Familhas". O logo é a palavra "Familhas" em branco com "Voe" em amarelo acima dela (descrição do responsável; o arquivo ainda não está no repositório). Por ser branco, precisa de fundo escuro ou colorido; o amarelo do "Voe" é a cor de acento da marca. Demais elementos de identidade são livres. Tom de voz não definido.

## Evidence on Hand
Sem fotos de destinos, depoimentos ou números de clientes confirmados no repositório; não fabricar. Dados de catálogo vêm dos mocks.

## Product Principles
1. Humano antes de automatizado: o WhatsApp do Enéas é sempre o caminho de saída.
2. Clareza no caminho até pagar: preço, passageiros e status da reserva sem ambiguidade.
3. Confiança de quem vai gastar com a família: sem urgência artificial nem claims inventados.
4. Celular primeiro.

## Accessibility & Inclusion
Contraste AA, alvos de toque de 44px, foco visível, navegação por teclado.
