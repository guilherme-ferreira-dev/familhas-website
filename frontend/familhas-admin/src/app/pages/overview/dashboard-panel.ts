import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import type { MonthCount, VoucherType } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { formatLocalDateTime, VOUCHER_TYPE } from '../../shared/crm-format';
import { LoadError } from '../../shared/load-error';

const HIDE_KEY = 'familhas-crm.hideValues';

/** Preferência de esconder números (botão de olho), por pessoa e navegador. Sem storage, mostra. */
export function readHidden(storage: Pick<Storage, 'getItem'> | null = safeStorage()): boolean {
  try {
    return storage?.getItem(HIDE_KEY) === '1';
  } catch {
    return false;
  }
}

function safeStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

const MONTH = new Intl.DateTimeFormat('pt-BR', {
  month: 'short',
  year: '2-digit',
  timeZone: 'UTC',
});

export function monthLabel(month: string): string {
  return MONTH.format(new Date(`${month}-01T00:00:00Z`)).replace('.', '');
}

/**
 * Painel operacional da visão geral (JSON: voos próximos e gráficos, com botão de olho; sem faturas). Os blocos
 * que a pessoa não pode ver chegam null da API e não aparecem.
 */
@Component({
  selector: 'app-dashboard-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LoadError],
  template: `
    <div class="toolbar">
      <button
        type="button"
        class="btn btn--small"
        [attr.aria-pressed]="hidden()"
        (click)="toggle()"
      >
        {{ hidden() ? '👁 Mostrar números' : '🙈 Ocultar números' }}
      </button>
    </div>
    @if (dashboard.error()) {
      <app-load-error [error]="dashboard.error()" (retry)="dashboard.reload()" />
    }
    @if (dashboard.value(); as d) {
      <ul class="kpis">
        @if (d.flights; as f) {
          <li class="card">
            <span class="muted">Voos nos próximos 7 dias</span>
            <strong>{{ show(f.next7Days) }}</strong>
          </li>
          <li class="card">
            <span class="muted">Voos nos próximos 30 dias</span>
            <strong>{{ show(f.next30Days) }}</strong>
          </li>
        }
        @if (d.vouchers; as v) {
          <li class="card">
            <span class="muted">Vouchers emitidos no mês</span>
            <strong>{{ show(v.issuedThisMonth) }}</strong>
            <span class="muted small">{{ byType(v.byTypeThisMonth) }}</span>
          </li>
        }
      </ul>

      @if (d.flights; as f) {
        <div class="grid">
          <section class="card" aria-labelledby="proximos">
            <h2 id="proximos">Próximos voos</h2>
            <ul class="plain">
              @for (u of f.upcoming; track u.id) {
                <li>
                  <a [routerLink]="['/voos-clientes', u.id]">{{ when(u.outboundAt) }}</a>
                  <span>{{ u.origin }} → {{ u.destination }} · {{ u.airline }}</span>
                  <span class="muted">{{ u.passengers.join(', ') }}</span>
                </li>
              } @empty {
                <li class="muted">Nenhum voo nos próximos 30 dias.</li>
              }
            </ul>
          </section>
          <section class="card" aria-labelledby="por-mes">
            <h2 id="por-mes">Voos por mês</h2>
            <div class="bars" aria-hidden="true">
              @for (m of f.byMonth; track m.month) {
                <div class="bar-col">
                  <span class="bar-value">{{ show(m.count) }}</span>
                  <span class="bar" [style.height.%]="hidden() ? 0 : height(m, f.byMonth)"></span>
                  <span class="bar-label">{{ label(m.month) }}</span>
                </div>
              }
            </div>
            <table class="visually-hidden">
              <caption>
                Voos por mês
              </caption>
              <tbody>
                @for (m of f.byMonth; track m.month) {
                  <tr>
                    <th scope="row">{{ label(m.month) }}</th>
                    <td>{{ show(m.count) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        </div>
      }
    }
  `,
  styles: `
    .toolbar {
      display: flex;
      justify-content: flex-end;
      margin-bottom: var(--space-2);
    }
    .kpis {
      list-style: none;
      margin: 0 0 var(--space-3);
      padding: 0;
      display: grid;
      gap: var(--space-3);
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
    }
    .kpis li {
      display: grid;
      gap: var(--space-1);
    }
    .kpis strong {
      font-size: 1.75rem;
    }
    .small {
      font-size: 0.8125rem;
    }
    .grid {
      display: grid;
      gap: var(--space-3);
      margin-bottom: var(--space-3);
    }
    @media (min-width: 60rem) {
      .grid {
        grid-template-columns: 3fr 2fr;
      }
    }
    h2 {
      margin: 0 0 var(--space-2);
      font-size: 1.125rem;
    }
    .plain {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--space-2);
    }
    .plain li {
      display: grid;
    }
    .bars {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: var(--space-2);
      align-items: end;
      height: 10rem;
    }
    .bar-col {
      display: grid;
      grid-template-rows: auto 1fr auto;
      height: 100%;
      text-align: center;
      font-size: 0.8125rem;
    }
    .bar {
      align-self: end;
      display: block;
      background: var(--color-primary);
      border-radius: 4px 4px 0 0;
      min-height: 2px;
    }
    .bar-label {
      color: var(--color-muted);
    }
  `,
})
export class DashboardPanel {
  private readonly api = inject(CrmApi);
  protected readonly hidden = signal(readHidden());
  protected readonly dashboard = rxResource({ stream: () => this.api.dashboard() });
  protected readonly max = computed(() =>
    Math.max(1, ...(this.dashboard.value()?.flights?.byMonth.map((m) => m.count) ?? [0])),
  );

  protected toggle(): void {
    this.hidden.update((h) => !h);
    try {
      localStorage.setItem(HIDE_KEY, this.hidden() ? '1' : '0');
    } catch {
      // sem storage: vale só nesta tela
    }
  }

  protected show(value: number): string {
    return this.hidden() ? '•••' : String(value);
  }

  protected byType(counts: Record<VoucherType, number>): string {
    if (this.hidden()) return '';
    return (Object.keys(VOUCHER_TYPE) as VoucherType[])
      .filter((t) => counts[t] > 0)
      .map((t) => `${VOUCHER_TYPE[t]} ${counts[t]}`)
      .join(' · ');
  }

  protected height(month: MonthCount, all: readonly MonthCount[]): number {
    const max = Math.max(1, ...all.map((m) => m.count));
    return (month.count / max) * 100;
  }

  protected when(value: string): string {
    return formatLocalDateTime(value);
  }

  protected label(month: string): string {
    return monthLabel(month);
  }
}
