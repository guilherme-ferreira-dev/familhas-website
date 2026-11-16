import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import type { VoucherStatus, VoucherType } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { VOUCHER_STATUS, VOUCHER_TYPE } from '../../shared/crm-format';
import { formatDate, formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { StatusBadge } from '../../shared/status-badge';

/** Histórico de vouchers emitidos, com busca por cliente ou número e filtro por tipo. */
@Component({
  selector: 'app-vouchers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Pager, LoadError, StatusBadge],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Emitir Voucher</h1>
          <p>Vouchers de voo, hotel, transfer e passeio. Mais recentes primeiro.</p>
        </div>
        @if (session.can('crm:vouchers:criar')) {
          <a class="btn btn--primary" routerLink="/vouchers/novo">Emitir voucher</a>
        }
      </div>

      <form class="filters" role="search" (submit)="search($event, busca.value, tipo.value)">
        <div class="field field--wide">
          <label for="busca">Cliente ou número (VF-…)</label>
          <input id="busca" #busca type="search" [value]="q()" maxlength="100" />
        </div>
        <div class="field">
          <label for="tipo">Tipo</label>
          <select id="tipo" #tipo [value]="tipoAtual()">
            <option value="">Todos</option>
            @for (t of types; track t) {
              <option [value]="t">{{ typeLabel(t) }}</option>
            }
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Buscar</button>
      </form>

      @if (vouchers.error()) {
        <app-load-error [error]="vouchers.error()" (retry)="vouchers.reload()" />
      }
      <div class="table-wrap" [attr.aria-busy]="vouchers.isLoading()">
        <table>
          <caption class="visually-hidden">
            Vouchers emitidos
          </caption>
          <thead>
            <tr>
              <th scope="col">Número</th>
              <th scope="col">Tipo</th>
              <th scope="col">Cliente</th>
              <th scope="col">Fornecedor</th>
              <th scope="col">Data do serviço</th>
              <th scope="col">Emitido</th>
              <th scope="col">Situação</th>
            </tr>
          </thead>
          <tbody>
            @for (v of vouchers.value()?.content ?? []; track v.id) {
              <tr>
                <td class="mono">
                  <a [routerLink]="['/vouchers', v.id]">{{ v.number }}</a>
                </td>
                <td>{{ typeLabel(v.type) }}</td>
                <td>{{ v.clientName }}</td>
                <td>{{ v.provider ?? '—' }}</td>
                <td class="num">{{ date(v.serviceDate) }}</td>
                <td>{{ dateTime(v.issuedAt) }} · {{ v.issuedBy ?? '—' }}</td>
                <td>
                  <app-status-badge
                    [label]="statusLabel(v.status)"
                    [tone]="v.status === 'ISSUED' ? 'success' : 'neutral'"
                  />
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="empty">
                  {{ vouchers.isLoading() ? 'Carregando…' : 'Nenhum voucher.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="vouchers.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
})
export class VouchersPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  protected readonly session = inject(CrmSession);

  readonly q = input<string>('');
  readonly tipo = input<string>('');
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly types = Object.keys(VOUCHER_TYPE) as VoucherType[];
  protected readonly tipoAtual = computed(() =>
    this.types.includes(this.tipo() as VoucherType) ? this.tipo() : '',
  );
  protected readonly vouchers = rxResource({
    params: () => ({ q: this.q(), type: this.tipoAtual(), page: this.pagina() }),
    stream: ({ params }) =>
      this.api.vouchers({
        q: params.q,
        type: (params.type || null) as VoucherType | null,
        page: params.page,
        size: 20,
      }),
  });

  constructor() {
    void this.session.ready();
  }

  protected typeLabel(type: VoucherType): string {
    return VOUCHER_TYPE[type];
  }

  protected statusLabel(status: VoucherStatus): string {
    return VOUCHER_STATUS[status];
  }

  protected date(value: string | null | undefined): string {
    return formatDate(value);
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected search(event: Event, q: string, type: string): void {
    event.preventDefault();
    void this.router.navigate([], { queryParams: { q: q.trim() || null, tipo: type || null } });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
