# ADR 0007: Reserva, Stripe Checkout e webhooks

- Status: aceito
- Data: 12/11/2026
- Fase: 6

## Contexto

A Fase 6 pede o fluxo cotação → reserva (`PENDING_PAYMENT`) → pagamento → webhook → `CONFIRMED`, com o Stripe atrás de uma interface `PaymentGateway`, moeda BRL, cartão e Pix, webhooks assinados e idempotentes, idempotency keys, reconciliação agendada e máquina de estados explícita (guia, seção 8). Os dados de cartão nunca podem passar pelos servidores da Familhas.

Antes desta fase:
- a cotação tinha só `OPEN` e `EXPIRED` e não reservava assento (ADR 0006);
- a API não tinha tabela de idempotência para eventos recebidos;
- o `SecurityConfig` exigia `ROLE_ADMIN` em qualquer `POST /api/**` fora de `/api/me`;
- a cotação aceitava moedas diferentes de BRL (o seed tem um hotel em USD).

Na primeira resposta da fase, cinco perguntas ficaram em aberto. As respostas do responsável estão em D1, D3, D6 e D8.

## Decisões

**D1. Stripe Checkout hospedado**
- A API cria uma Checkout Session (`mode=payment`) e devolve a `checkoutUrl`. O front só redireciona. A página de pagamento, o 3-D Secure e o QR Code do Pix ficam no Stripe.
- Uma sessão por tentativa e por método: `card` para `CREDIT_CARD`, `pix` para `PIX`.
- As URLs de retorno vêm de `FAMILHAS_WEB_BASE_URL`, nunca do cliente (evita open redirect): `/checkout/{bookingId}/sucesso` e `/checkout/{bookingId}/cancelado`.
- A volta para "sucesso" não confirma nada. Só o webhook (ou a reconciliação, D7) muda o status. O front faz polling em `GET /api/me/bookings/{id}`.
- O Stripe recebe só o valor, a descrição da reserva, o e-mail do cliente (pré-preenchido, para o recibo) e metadados com `bookingId` e `paymentId`. Nada de CPF, telefone ou itens detalhados (LGPD).
- O front fica atrás de uma interface própria: trocar por Payment Element depois muda só o adapter do front e o método do `PaymentGateway`.

**D2. Contrato publicado antes do código**
- `docs/api/checkout.yaml` (OpenAPI 3.1) foi escrito antes da implementação para a Loja (Fase 7) consumir, e é validado por teste.
- Todo erro de negócio leva um `code` estável no Problem Details (ex.: `QUOTE_EXPIRED`, `SEATS_UNAVAILABLE`).

**D3. Reserva e bloqueio de assentos**
- `POST /api/me/bookings` aceita só cotação `OPEN`, dentro da validade, em BRL e do próprio cliente. Reconsulta as ofertas no provedor e copia o snapshot de preços da cotação (o total cobrado é o da cotação).
- Uma cotação vira no máximo uma reserva (`UNIQUE` em `bookings.quote_id`), e a cotação passa a `BOOKED`. Repetir o POST devolve 409 com o `bookingId` existente.
- Os assentos do voo do catálogo curado são descontados na criação da reserva, com update condicional (`seats_available >= n`) para não haver overbooking com reservas simultâneas. A reserva tem 30 minutos (`BOOKING_PAYMENT_WINDOW`) para ser paga.
- Hotel e carro não têm estoque por data no catálogo curado (pendência da Fase 5): não há bloqueio para eles.
- Os assentos voltam quando a reserva é cancelada (prazo vencido ou estorno total).

**D4. Máquinas de estado**
- Reserva: `PENDING_PAYMENT → CONFIRMED → COMPLETED`; `PENDING_PAYMENT → CANCELLED` (prazo vencido); `CONFIRMED → CANCELLED` (estorno total). `COMPLETED` fica para quando a viagem termina (Fase 8).
- Pagamento: `PENDING → PROCESSING → SUCCEEDED`; `PENDING|PROCESSING → FAILED`; `FAILED → PROCESSING|SUCCEEDED` (nova tentativa na mesma sessão); `PENDING|PROCESSING|FAILED → CANCELED`; `CANCELED → SUCCEEDED` (o cliente concluiu a sessão no instante em que ela foi substituída: o dinheiro entrou e precisa ser registrado); `SUCCEEDED → PARTIALLY_REFUNDED|REFUNDED`; `PARTIALLY_REFUNDED → PARTIALLY_REFUNDED|REFUNDED`.
- As transições ficam nos enums. Transição inválida vinda de webhook é ignorada e logada, sem erro: cobre eventos fora de ordem e repetidos com outro `event.id`.
- Pagamento parado não prende a reserva para sempre: a sessão expira em 30 minutos (mínimo do Stripe), e o Pix também expira em 30 minutos. Enquanto houver sessão aberta ou Pix em processamento, a reserva não é cancelada. O bloqueio máximo é de cerca de 1 hora (30 minutos da reserva e mais 30 da última sessão aberta dentro do prazo).
- Se um pagamento for aprovado com a reserva já cancelada (corrida rara entre expiração e pagamento) ou já paga por outra tentativa (cobrança em dobro), a API pede o estorno total automaticamente e registra um aviso no log. O estorno de uma tentativa duplicada não cancela a reserva paga pela outra.
- Quando um pagamento confirma a reserva, as outras sessões ainda abertas da reserva são expiradas no Stripe.
- Valor ou moeda do pagamento aprovado diferente do registrado: nada é confirmado e o log registra um erro para conferência no Dashboard.

**D5. Checkout idempotente pelo domínio**
- `POST /api/me/bookings/{id}/checkout-session`: se já houver sessão aberta com o mesmo método, devolve a mesma; com outro método, expira a anterior no Stripe e cria outra.
- Sem header `Idempotency-Key` vindo do front: a regra do domínio já impede duplicidade e o CORS fica como está.
- Toda chamada que escreve no Stripe leva uma idempotency key derivada do pagamento (`checkout-session:{paymentId}`, `expire:{paymentId}`, `refund:{paymentId}`), gravada em `payments.idempotency_key`. Um retry depois de timeout não cria uma segunda sessão nem um segundo estorno.

**D6. Webhooks**
- `POST /api/webhooks/stripe`, rota pública no `SecurityConfig`, autenticada pela `Stripe-Signature`. A validação usa `Webhook.constructEvent` sobre o corpo cru, com tolerância de 5 minutos contra replay.
- Idempotência por `event.id` na tabela `stripe_webhook_events` (chave primária = `event.id`). O registro do evento e a mudança de domínio ficam na mesma transação: ou os dois acontecem, ou nenhum. Evento repetido responde 200 sem efeito. Falha no processamento responde 500, e o Stripe tenta de novo.
- Eventos tratados:

  | Evento | Efeito |
  |---|---|
  | `checkout.session.completed` | `paid`: pagamento `SUCCEEDED` e reserva `CONFIRMED`. `unpaid` (Pix): pagamento `PROCESSING`. Guarda o `payment_intent`. |
  | `checkout.session.async_payment_succeeded` | Pix pago: `SUCCEEDED` e `CONFIRMED`. |
  | `checkout.session.async_payment_failed` | Pix não pago: `FAILED`. |
  | `checkout.session.expired` | Sessão vencida: `CANCELED`. |
  | `payment_intent.processing` | `PROCESSING`. |
  | `payment_intent.succeeded` | `SUCCEEDED` e `CONFIRMED` (redundante com a sessão; a máquina de estados absorve). |
  | `payment_intent.payment_failed` | `FAILED`. O cliente pode tentar de novo na mesma sessão. |
  | `charge.refunded` | Estorno total: `REFUNDED` e reserva `CANCELLED`. Parcial: `PARTIALLY_REFUNDED` e a reserva continua `CONFIRMED`. |

- O pagamento é localizado por `metadata.paymentId` (copiado para o PaymentIntent via `payment_intent_data.metadata`) ou, na falta dele, pelo id da sessão ou do PaymentIntent. Evento de objeto desconhecido é registrado e ignorado.
- O payload do evento não é gravado nem logado. Os logs usam só `event.id`, o tipo e os ids internos.
- Os estornos são feitos no Dashboard do Stripe até o Admin ganhar essa ação (Fase 10). O webhook mantém a API em dia.

**D7. Reconciliação**
- Um job agendado (`PAYMENTS_RECONCILIATION_FIXED_DELAY`, padrão 5 minutos; `PAYMENTS_RECONCILIATION_ENABLED` liga e desliga):
  - pagamentos `PENDING`, `PROCESSING` ou `FAILED` sem novidade há mais de 10 minutos (`PAYMENTS_RECONCILIATION_STALE_AFTER`): consulta a sessão no Stripe e aplica o mesmo processamento do webhook;
  - reservas `PENDING_PAYMENT` vencidas: antes de cancelar, consulta no Stripe as tentativas ainda não encerradas (um pagamento do último segundo confirma a reserva em vez de virar estorno). Sem pagamento ativo, `CANCELLED` (`PAYMENT_EXPIRED`) e os assentos voltam.
- Cada item roda na própria transação, com a reserva travada e o estado reavaliado depois do lock. Duas instâncias rodando ao mesmo tempo não causam efeito em dobro, e a falha de um item não interrompe os outros.
- Sem `STRIPE_SECRET_KEY`, o job só expira reservas.
- Cobre webhook perdido, Stripe CLI desligado no ambiente local e queda da API.

**D8. Chaves e ambiente**
- `STRIPE_SECRET_KEY` e `STRIPE_WEBHOOK_SECRET` só por variável de ambiente. A API recusa subir com uma chave que não seja de teste (`sk_test_` ou `rk_test_`). Liberar chave live exige um novo ADR.
- Sem chave, a API sobe normalmente e o checkout responde 503: o resto da Loja funciona sem Stripe.
- `STRIPE_PIX_ENABLED` liga ou desliga o Pix (422 `PAYMENT_METHOD_UNAVAILABLE` quando desligado), porque o Pix depende da conta.
- Desenvolvimento local com `stripe listen --forward-to localhost:8080/api/webhooks/stripe`. O `whsec_` impresso pelo CLI vai em `STRIPE_WEBHOOK_SECRET`.
- A versão da API do Stripe é a fixada pela versão do `stripe-java` no `pom.xml`. Atualizar o SDK é uma mudança revisada, não automática.

**D9. Dinheiro**
- O valor cobrado é o `total_amount` da reserva, em BRL, convertido para centavos com `movePointRight(2).longValueExact()`: valor com mais de 2 casas é erro, nunca arredondamento silencioso.
- Cotação em outra moeda não vira reserva (422 `CURRENCY_NOT_SUPPORTED`). Não há câmbio no sistema.

**D10. Eventos de integração**
- `BookingCreated` (`booking.booking-created.v1`), `PaymentConfirmed` (`payment.payment-confirmed.v1`) e `PaymentFailed` (`payment.payment-failed.v1`), pelo outbox (ADR 0005), na mesma transação da mudança de status.
- O `familhas-email` envia o e-mail de cada um. A grade do Admin (Fase 10) consome os mesmos eventos.
- `BookingConfirmed`, previsto em `docs/events/README.md`, fica para a Fase 10, se o Admin precisar: na Fase 6, `PaymentConfirmed` já implica reserva confirmada.

**D11. Testes sem conta Stripe**
- O adapter Stripe é testado contra o `stripe/stripe-mock` em Testcontainers.
- Os webhooks são testados com payloads assinados por um secret de teste, do mesmo jeito que o Stripe assina.
- Assim `./mvnw verify` e o CI rodam sem rede externa e sem chave. A validação com a conta real é manual (`stripe listen`).

## Consequências

- **Rota pública nova:** `/api/webhooks/stripe` fica fora do JWT. A proteção é a assinatura, e a rota não aceita outro método.
- **Assentos bloqueados até ~1 hora** por reserva não paga. Com poucos assentos (o seed tem um voo com 4), clientes desistentes seguram o voo por esse tempo.
- **Dependência do webhook ou da reconciliação:** com o Stripe CLI desligado no ambiente local, a confirmação só chega pela reconciliação (até 5 minutos).
- **Estorno parcial não muda a reserva:** fica para o Enéas decidir caso a caso no Admin (Fase 10).
- **Parcelamento fora:** o pagamento é sempre à vista (ADR 0008).
- **Evoluções só documentadas:**
  - Payment Element no lugar do Checkout hospedado;
  - Customer do Stripe e cartão salvo;
  - estorno pelo Admin;
  - chave live e conta em produção.
