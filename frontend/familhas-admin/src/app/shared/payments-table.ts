import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { AdminPayment } from '../core/api/api-types';
import { formatDateTime, money, shortId } from './format';
import { PAYMENT_METHOD, PAYMENT_STATUS } from './labels';
import { StatusBadge } from './status-badge';

/** Linhas da planilha de pagamentos. `highlight` marca linhas recém-atualizadas pelo tempo real. */
@Component({
  selector: 'app-payments-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, StatusBadge],
  template: `
    <div class="table-wrap">
      <table>
        <caption class="visually-hidden">
          {{
            caption()
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">Pagamento</th>
            <th scope="col">Reserva</th>
            @if (showClient()) {
              <th scope="col">Cliente</th>
            }
            <th scope="col">Método</th>
            <th scope="col">Status</th>
            <th scope="col" class="num">Parcelas</th>
            <th scope="col" class="num">Valor</th>
            <th scope="col" class="num">Estornado</th>
            <th scope="col">Pago em</th>
            <th scope="col">Criado em</th>
          </tr>
        </thead>
        <tbody>
          @for (p of rows(); track p.id) {
            <tr [class.fresh]="highlight().has(p.id)">
              <td class="mono">{{ short(p.id) }}</td>
              <td>
                <a class="mono" [routerLink]="['/reservas', p.bookingId]">{{
                  short(p.bookingId)
                }}</a>
              </td>
              @if (showClient()) {
                <td>
                  <a [routerLink]="['/clientes', p.client.id]">{{ p.client.name }}</a>
                  <div class="muted small">{{ p.client.email }}</div>
                </td>
              }
              <td>{{ method(p) }}</td>
              <td>
                <app-status-badge [label]="status(p).label" [tone]="status(p).tone" />
                @if (p.failureCode) {
                  <div class="muted small">{{ p.failureCode }}</div>
                }
              </td>
              <td class="num">{{ p.installments }}</td>
              <td class="num">{{ money(p.amount, p.currency) }}</td>
              <td class="num">
                {{ p.refundedAmount > 0 ? money(p.refundedAmount, p.currency) : '—' }}
              </td>
              <td>{{ dateTime(p.paidAt) }}</td>
              <td>{{ dateTime(p.createdAt) }}</td>
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="showClient() ? 10 : 9" class="empty">Nenhum pagamento.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .small {
      font-size: 0.8125rem;
    }
    .fresh {
      animation: fresh 3s ease-out;
    }
    @keyframes fresh {
      from {
        background: var(--color-warning-bg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .fresh {
        animation: none;
        background: var(--color-warning-bg);
      }
    }
  `,
})
export class PaymentsTable {
  readonly rows = input.required<readonly AdminPayment[]>();
  readonly showClient = input(true);
  readonly caption = input('Pagamentos');
  readonly highlight = input<ReadonlySet<string>>(new Set());

  protected readonly money = money;
  protected readonly dateTime = formatDateTime;
  protected readonly short = shortId;
  protected status(p: AdminPayment) {
    return PAYMENT_STATUS[p.status];
  }
  protected method(p: AdminPayment) {
    return PAYMENT_METHOD[p.method];
  }
}
