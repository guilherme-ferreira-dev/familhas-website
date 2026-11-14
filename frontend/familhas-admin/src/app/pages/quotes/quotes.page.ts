import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import type { AdminQuoteSummary, QuoteOrigin, QuoteStatus } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { formatDateTime, money, shortId } from '../../shared/format';
import { options, QUOTE_ORIGIN, QUOTE_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { StatusBadge } from '../../shared/status-badge';

/** Cotações da Loja e manuais. Estado na URL (?origem=&status=&pagina=). */
@Component({
  selector: 'app-quotes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Pager, StatusBadge, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Cotações</h1>
          <p>
            As da Loja e as manuais, feitas aqui para um cliente (por exemplo, depois de um
            atendimento no WhatsApp).
          </p>
        </div>
        <a class="btn btn--primary" routerLink="/cotacoes/nova">Nova cotação manual</a>
      </div>

      <form class="filters" (submit)="apply($event, originEl.value, statusEl.value)">
        <div class="field">
          <label for="origem">Origem</label>
          <select id="origem" #originEl>
            <option value="">Todas</option>
            @for (o of originOptions; track o.value) {
              <option [value]="o.value" [selected]="o.value === origem()">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="status">Status</label>
          <select id="status" #statusEl>
            <option value="">Todos</option>
            @for (o of statusOptions; track o.value) {
              <option [value]="o.value" [selected]="o.value === status()">{{ o.label }}</option>
            }
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Filtrar</button>
      </form>

      @if (quotes.error()) {
        <app-load-error [error]="quotes.error()" (retry)="quotes.reload()" />
      }
      <div class="table-wrap">
        <table>
          <caption class="visually-hidden">
            Cotações
          </caption>
          <thead>
            <tr>
              <th scope="col">Cotação</th>
              <th scope="col">Status</th>
              <th scope="col">Origem</th>
              <th scope="col">Cliente</th>
              <th scope="col" class="num">Pax</th>
              <th scope="col" class="num">Total</th>
              <th scope="col">Válida até</th>
              <th scope="col">Criada em</th>
            </tr>
          </thead>
          <tbody>
            @for (q of quotes.value()?.content ?? []; track q.id) {
              <tr>
                <td>
                  <a class="mono" [routerLink]="['/cotacoes', q.id]">{{ short(q.id) }}</a>
                </td>
                <td><app-status-badge [label]="statusOf(q).label" [tone]="statusOf(q).tone" /></td>
                <td>{{ originOf(q) }}</td>
                <td>
                  <a [routerLink]="['/clientes', q.client.id]">{{ q.client.name }}</a>
                </td>
                <td class="num">{{ q.passengers }}</td>
                <td class="num">{{ money(q.totalAmount, q.currency) }}</td>
                <td>{{ dateTime(q.expiresAt) }}</td>
                <td>{{ dateTime(q.createdAt) }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" class="empty">Nenhuma cotação.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="quotes.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
})
export class QuotesPage {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);

  readonly origem = input<QuoteOrigin | undefined>();
  readonly status = input<QuoteStatus | undefined>();
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly originOptions = options(QUOTE_ORIGIN);
  protected readonly statusOptions = options(QUOTE_STATUS);
  protected readonly money = money;
  protected readonly dateTime = formatDateTime;
  protected readonly short = shortId;

  protected readonly quotes = rxResource({
    params: () => ({
      origin: this.origem() || null,
      status: this.status() || null,
      page: this.pagina(),
    }),
    stream: ({ params }) => this.api.quotes({ ...params, size: 20, sort: 'createdAt,desc' }),
  });

  protected statusOf(q: AdminQuoteSummary) {
    return QUOTE_STATUS[q.status];
  }

  protected originOf(q: AdminQuoteSummary) {
    return QUOTE_ORIGIN[q.origin];
  }

  protected apply(event: Event, origem: string, status: string): void {
    event.preventDefault();
    void this.router.navigate([], {
      queryParams: { origem: origem || null, status: status || null },
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
