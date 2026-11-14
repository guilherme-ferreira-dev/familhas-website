import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../../core/admin/admin-api';
import { formatDate, formatDateTime, shortId } from '../../shared/format';
import { BOOKING_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { PaymentsTable } from '../../shared/payments-table';
import { PriceSnapshot } from '../../shared/price-snapshot';
import { StatusBadge } from '../../shared/status-badge';

const CANCELLATION: Record<string, string> = {
  PAYMENT_EXPIRED: 'prazo de pagamento vencido',
  REFUNDED: 'estorno total',
};

/** Reserva: cliente, prazos, snapshot de preço (custo e margem) e todas as tentativas de pagamento. */
@Component({
  selector: 'app-booking-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PaymentsTable, PriceSnapshot, StatusBadge, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/reservas">← Reservas</a></p>
      @if (booking.error()) {
        <app-load-error [error]="booking.error()" (retry)="booking.reload()" />
      }
      @if (booking.value(); as b) {
        <div class="page-header">
          <div>
            <h1 id="titulo">
              Reserva <span class="mono">{{ short(b.id) }}</span>
            </h1>
            <p>
              <app-status-badge [label]="status(b.status).label" [tone]="status(b.status).tone" />
              @if (b.cancellationReason) {
                · cancelada por {{ cancellation(b.cancellationReason) }}
              }
            </p>
          </div>
        </div>

        <dl class="card details">
          <div>
            <dt>Cliente</dt>
            <dd>
              <a [routerLink]="['/clientes', b.client.id]">{{ b.client.name }}</a><br />{{
                b.client.email
              }}
            </dd>
          </div>
          <div>
            <dt>Pacote</dt>
            <dd>{{ b.travelPackageTitle ?? 'Itens avulsos' }}</dd>
          </div>
          <div>
            <dt>Passageiros</dt>
            <dd>{{ b.passengers }}</dd>
          </div>
          <div>
            <dt>Início da viagem</dt>
            <dd>{{ date(b.startDate) }}</dd>
          </div>
          <div>
            <dt>Criada em</dt>
            <dd>{{ dateTime(b.createdAt) }}</dd>
          </div>
          <div>
            <dt>Prazo para pagar</dt>
            <dd>{{ dateTime(b.expiresAt) }}</dd>
          </div>
          <div>
            <dt>Confirmada em</dt>
            <dd>{{ dateTime(b.confirmedAt) }}</dd>
          </div>
          <div>
            <dt>Cancelada em</dt>
            <dd>{{ dateTime(b.cancelledAt) }}</dd>
          </div>
          <div>
            <dt>Cotação</dt>
            <dd>
              <a class="mono" [routerLink]="['/cotacoes', b.quoteId]">{{ short(b.quoteId) }}</a>
            </dd>
          </div>
          <div>
            <dt>Código completo</dt>
            <dd class="mono">{{ b.id }}</dd>
          </div>
        </dl>

        <section aria-labelledby="preco" class="stack">
          <h2 id="preco">Preço</h2>
          <app-price-snapshot [snapshot]="b" />
        </section>

        <section aria-labelledby="pagamentos" class="stack">
          <h2 id="pagamentos">Tentativas de pagamento</h2>
          <app-payments-table
            [rows]="b.payments"
            [showClient]="false"
            caption="Tentativas de pagamento"
          />
          <p class="muted">
            Estornos são feitos no Dashboard do Stripe; o webhook atualiza a reserva.
          </p>
        </section>
      } @else if (booking.isLoading()) {
        <p class="muted" role="status">Carregando reserva…</p>
      }
    </section>
  `,
  styles: `
    .stack {
      display: grid;
      gap: var(--space-2);
    }
  `,
})
export class BookingDetailPage {
  private readonly api = inject(AdminApi);
  readonly id = input.required<string>();

  protected readonly booking = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.booking(params),
  });
  protected readonly short = shortId;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected status(status: keyof typeof BOOKING_STATUS) {
    return BOOKING_STATUS[status];
  }

  protected cancellation(reason: string): string {
    return CANCELLATION[reason] ?? reason;
  }
}
