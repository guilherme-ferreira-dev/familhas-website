import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../../core/admin/admin-api';
import { formatDateTime, shortId } from '../../shared/format';
import { QUOTE_ORIGIN, QUOTE_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { PriceSnapshot } from '../../shared/price-snapshot';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-quote-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PriceSnapshot, StatusBadge, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/cotacoes">← Cotações</a></p>
      @if (quote.error()) {
        <app-load-error [error]="quote.error()" (retry)="quote.reload()" />
      }
      @if (quote.value(); as q) {
        <div class="page-header">
          <div>
            <h1 id="titulo">
              Cotação <span class="mono">{{ short(q.id) }}</span>
            </h1>
            <p>
              <app-status-badge [label]="status(q.status).label" [tone]="status(q.status).tone" />
              · {{ origin(q.origin) }}
              @if (q.createdBy) {
                por {{ q.createdBy }}
              }
            </p>
          </div>
          @if (q.bookingId) {
            <a class="btn" [routerLink]="['/reservas', q.bookingId]">Ver reserva</a>
          }
        </div>

        @if (q.origin === 'ADMIN' && q.status === 'OPEN') {
          <p class="alert alert--info">
            O cliente já vê esta cotação na Loja, em "Minhas cotações", e pode reservar e pagar até
            {{ dateTime(q.expiresAt) }}. Código para o WhatsApp:
            <strong class="mono">{{ short(q.id) }}</strong>.
          </p>
        }

        <dl class="card details">
          <div>
            <dt>Cliente</dt>
            <dd>
              <a [routerLink]="['/clientes', q.client.id]">{{ q.client.name }}</a><br />{{
                q.client.email
              }}
            </dd>
          </div>
          <div>
            <dt>Passageiros</dt>
            <dd>{{ q.passengers }}</dd>
          </div>
          <div>
            <dt>Criada em</dt>
            <dd>{{ dateTime(q.createdAt) }}</dd>
          </div>
          <div>
            <dt>Válida até</dt>
            <dd>{{ dateTime(q.expiresAt) }}</dd>
          </div>
          @if (q.internalNote) {
            <div class="wide">
              <dt>Nota interna (o cliente não vê)</dt>
              <dd>{{ q.internalNote }}</dd>
            </div>
          }
        </dl>

        <section aria-labelledby="preco" class="stack">
          <h2 id="preco">Preço</h2>
          <app-price-snapshot [snapshot]="q" />
        </section>
      } @else if (quote.isLoading()) {
        <p class="muted" role="status">Carregando cotação…</p>
      }
    </section>
  `,
  styles: `
    .stack {
      display: grid;
      gap: var(--space-2);
    }
    .wide {
      grid-column: 1 / -1;
    }
  `,
})
export class QuoteDetailPage {
  private readonly api = inject(AdminApi);
  readonly id = input.required<string>();

  protected readonly quote = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.quote(params),
  });
  protected readonly short = shortId;
  protected readonly dateTime = formatDateTime;

  protected status(s: keyof typeof QUOTE_STATUS) {
    return QUOTE_STATUS[s];
  }

  protected origin(o: keyof typeof QUOTE_ORIGIN) {
    return QUOTE_ORIGIN[o];
  }
}
