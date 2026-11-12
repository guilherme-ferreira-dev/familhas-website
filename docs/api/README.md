# Contratos HTTP (OpenAPI)

| Arquivo | Escopo | Mantido por |
|---|---|---|
| [checkout.yaml](checkout.yaml) | Cotação (`/api/me/quotes`), reserva e checkout (`/api/me/bookings/**`) e webhook do Stripe (`/api/webhooks/stripe`) | Fase 6 |
| `familhas-api.yaml` | Catálogo e busca | Fase 7 |

- OpenAPI 3.1, escrito à mão antes da implementação para a Loja (Fase 7) consumir. A geração por Springdoc, por grupo (public e admin), entra na Fase 11.
- `checkout.yaml` é validado em `backend/familhas-api` por `CheckoutOpenApiContractTests` (`./mvnw verify`).
- Mudança incompatível em um endpoint já publicado exige combinar com quem consome antes de mudar o arquivo.
