import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { BookingStatus, Dashboard, PaymentStatus } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { daysBefore, money, todayInSaoPaulo } from '../../shared/format';
import { BOOKING_STATUS, PAYMENT_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { StatusBadge } from '../../shared/status-badge';
import { DailyValue, RevenueChart } from './revenue-chart';

const PRESETS = [7, 30, 90] as const;

@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, RevenueChart, StatusBadge, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Dashboard</h1>
          <p>Vendas, reservas e clientes do período. Datas no horário de São Paulo.</p>
        </div>
        <form class="filters" (submit)="$event.preventDefault()">
          <div class="field">
            <span class="label" id="atalhos">Período</span>
            <div class="actions" role="group" aria-labelledby="atalhos">
              @for (days of presets; track days) {
                <button
                  type="button"
                  class="btn btn--small"
                  [attr.aria-pressed]="preset() === days"
                  (click)="applyPreset(days)"
                >
                  {{ days }} dias
                </button>
              }
            </div>
          </div>
          <div class="field">
            <label for="de">De</label>
            <input
              id="de"
              type="date"
              [ngModel]="from()"
              (ngModelChange)="setFrom($event)"
              name="de"
            />
          </div>
          <div class="field">
            <label for="ate">Até</label>
            <input
              id="ate"
              type="date"
              [ngModel]="to()"
              (ngModelChange)="setTo($event)"
              name="ate"
            />
          </div>
        </form>
      </div>

      @if (data.error()) {
        <app-load-error [error]="data.error()" (retry)="data.reload()" />
      }

      @if (data.value(); as d) {
        <ul class="kpis" aria-label="Indicadores">
          <li class="card">
            <span class="kpi-label">Receita líquida</span>
            <span class="kpi-value">{{ brl(d).net }}</span>
            <span class="muted"
              >{{ brl(d).count }} pagamento(s) · {{ brl(d).refunded }} estornado</span
            >
          </li>
          <li class="card">
            <span class="kpi-label">Reservas aguardando pagamento</span>
            <span class="kpi-value">{{ d.pendingBookings }}</span>
            <a routerLink="/reservas" [queryParams]="{ status: 'PENDING_PAYMENT' }">Ver reservas</a>
          </li>
          <li class="card">
            <span class="kpi-label">Cotações válidas</span>
            <span class="kpi-value">{{ d.openQuotes }}</span>
            <a routerLink="/cotacoes" [queryParams]="{ status: 'OPEN' }">Ver cotações</a>
          </li>
          <li class="card">
            <span class="kpi-label">Clientes novos no período</span>
            <span class="kpi-value">{{ d.newClients }}</span>
            <a routerLink="/clientes" [queryParams]="{ aba: 'NEW' }">Ver novos</a>
          </li>
        </ul>

        <section class="card" aria-labelledby="grafico">
          <h2 id="grafico">Receita líquida por dia (BRL)</h2>
          <p class="muted small">
            Pagamentos aprovados pelo dia do pagamento, menos o que já foi estornado.
          </p>
          <app-revenue-chart [series]="daily()" />
        </section>

        <div class="grid-2">
          <section class="card" aria-labelledby="pagamentos">
            <h2 id="pagamentos">Pagamentos criados no período</h2>
            <table>
              <tbody>
                @for (row of paymentRows(); track row.status) {
                  <tr>
                    <th scope="row">
                      <app-status-badge
                        [label]="paymentLabel(row.status).label"
                        [tone]="paymentLabel(row.status).tone"
                      />
                    </th>
                    <td class="num">
                      <a
                        routerLink="/pagamentos"
                        [queryParams]="{ status: row.status, de: d.from, ate: d.to }"
                        >{{ row.count }}</a
                      >
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
          <section class="card" aria-labelledby="reservas">
            <h2 id="reservas">Reservas criadas no período</h2>
            <table>
              <tbody>
                @for (row of bookingRows(); track row.status) {
                  <tr>
                    <th scope="row">
                      <app-status-badge
                        [label]="bookingLabel(row.status).label"
                        [tone]="bookingLabel(row.status).tone"
                      />
                    </th>
                    <td class="num">
                      <a
                        routerLink="/reservas"
                        [queryParams]="{ status: row.status, de: d.from, ate: d.to }"
                        >{{ row.count }}</a
                      >
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        </div>
      } @else if (data.isLoading()) {
        <p class="muted" role="status">Carregando indicadores…</p>
      }
    </section>
  `,
  styles: `
    .kpis {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--space-3);
      grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
    }
    .kpis li {
      display: grid;
      gap: var(--space-1);
      align-content: start;
    }
    .kpi-label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--color-muted);
    }
    .kpi-value {
      font-size: 1.75rem;
      font-weight: 650;
      font-variant-numeric: tabular-nums;
    }
    .small {
      margin: var(--space-1) 0 var(--space-3);
      font-size: 0.8125rem;
    }
    .grid-2 {
      display: grid;
      gap: var(--space-3);
      grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr));
    }
    .grid-2 h2 {
      margin-bottom: var(--space-2);
    }
    th {
      font-weight: 400;
    }
    [aria-pressed='true'] {
      border-color: var(--color-primary);
      background: #e6eeff;
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(AdminApi);

  protected readonly presets = PRESETS;
  protected readonly to = signal(todayInSaoPaulo());
  protected readonly from = signal(daysBefore(this.to(), 29));
  protected readonly preset = signal<number | null>(30);

  protected readonly data = rxResource({
    params: () => ({ from: this.from(), to: this.to() }),
    stream: ({ params }) => this.api.dashboard(params.from, params.to),
  });

  /** Série contínua: dias sem venda entram com zero (o gráfico não pula dias). */
  protected readonly daily = computed<DailyValue[]>(() => {
    const d = this.data.value();
    if (!d) return [];
    const byDay = new Map(
      d.dailyRevenue.filter((r) => r.currency === 'BRL').map((r) => [r.date, r.net]),
    );
    return eachDay(d.from, d.to).map((date) => ({ date, value: byDay.get(date) ?? 0 }));
  });

  protected readonly paymentRows = computed(() =>
    rows<PaymentStatus>(this.data.value()?.paymentsByStatus),
  );
  protected readonly bookingRows = computed(() =>
    rows<BookingStatus>(this.data.value()?.bookingsByStatus),
  );

  protected paymentLabel(status: PaymentStatus) {
    return PAYMENT_STATUS[status];
  }

  protected bookingLabel(status: BookingStatus) {
    return BOOKING_STATUS[status];
  }

  protected brl(d: Dashboard) {
    const r = d.revenue.find((x) => x.currency === 'BRL');
    return {
      net: money(r?.net ?? 0),
      refunded: money(r?.refunded ?? 0),
      count: r?.count ?? 0,
    };
  }

  protected applyPreset(days: number): void {
    const to = todayInSaoPaulo();
    this.to.set(to);
    this.from.set(daysBefore(to, days - 1));
    this.preset.set(days);
  }

  protected setFrom(value: string): void {
    if (value) {
      this.from.set(value);
      this.preset.set(null);
    }
  }

  protected setTo(value: string): void {
    if (value) {
      this.to.set(value);
      this.preset.set(null);
    }
  }
}

function rows<S extends string>(counts: Record<string, number> | undefined) {
  return Object.entries(counts ?? {}).map(([status, count]) => ({ status: status as S, count }));
}

export function eachDay(from: string, to: string): string[] {
  const days: string[] = [];
  const cursor = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  while (cursor <= end && days.length < 400) {
    days.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return days;
}
