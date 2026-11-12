# ADR 0008: Parcelamento no cartão

- Status: aceito (parcelamento adiado)
- Data: 12/11/2026
- Fase: 6

## Contexto

O guia pede pagamento "com parcelamento" na Loja e um `Installment` no domínio. Também pede que, antes de implementar, se valide a disponibilidade de parcelamento na conta Stripe Brasil e as regras vigentes, e que o resultado seja documentado em ADR (guia, seções 7, 8 e 11).

Ainda não há conta Stripe Brasil verificada para essa validação.

## Decisão

- A Fase 6 **não implementa parcelamento**. Todo pagamento com cartão é à vista.
- O modelo já comporta parcelas: `payments.installments` (sempre 1) e a tabela `installments` com uma linha por pagamento. Ligar o parcelamento depois não muda o contrato da API, só o valor aceito (`installments` deixa de ser fixo em 1).
- Nada de parcelamento é enviado ao Stripe (`payment_method_options.card.installments` fica desligado).

## O que validar antes de implementar

Na conta Stripe Brasil, em modo de teste e depois em produção:
1. Se o parcelamento no cartão está disponível para a conta e para o Stripe Checkout hospedado (D1 do ADR 0007), ou só para PaymentIntent com Payment Element.
2. Quantidade de parcelas permitida, valor mínimo por parcela e bandeiras aceitas.
3. Quem paga os juros: parcelamento sem juros (lojista) ou com juros (cliente), e as taxas do Stripe para cada caso.
4. Como chega o dinheiro: à vista ou parcela a parcela, e o efeito em estorno total e parcial.
5. Regras do Enéas: número máximo de parcelas, valor mínimo e se varia por pacote.

## Consequências

- A Loja (Fase 7) mostra só "à vista" no cartão e no Pix.
- Implementar o parcelamento exige um novo ADR com o resultado das validações, que substitui este.
