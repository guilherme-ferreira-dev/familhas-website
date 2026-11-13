import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { AdminBookingSummary } from '../core/api/api-types';
import { formatDate, formatDateTime, money, shortId } from './format';
import { BOOKING_STATUS, PAYMENT_STATUS } from './labels';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'app-bookings-table',
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
            <th scope="col">Reserva</th>
            <th scope="col">Status</th>
            @if (showClient()) {
              <th scope="col">Cliente</th>
            }
            <th scope="col">Pacote</th>
            <th scope="col">Início</th>
            <th scope="col" class="num">Pax</th>
            <th scope="col" class="num">Total</th>
            <th scope="col">Pagamento</th>
            <th scope="col">Criada em</th>
          </tr>
        </thead>
        <tbody>
          @for (b of rows(); track b.id) {
            <tr>
              <td>
                <a class="mono" [routerLink]="['/reservas', b.id]">{{ short(b.id) }}</a>
              </td>
              <td><app-status-badge [label]="status(b).label" [tone]="status(b).tone" /></td>
              @if (showClient()) {
                <td>
                  <a [routerLink]="['/clientes', b.client.id]">{{ b.client.name }}</a>
                  <div class="muted small">{{ b.client.email }}</div>
                </td>
              }
              <td>{{ b.travelPackageTitle ?? 'Itens avulsos' }}</td>
              <td>{{ date(b.startDate) }}</td>
              <td class="num">{{ b.passengers }}</td>
              <td class="num">{{ money(b.totalAmount, b.currency) }}</td>
              <td>
                @if (b.latestPaymentStatus; as p) {
                  <app-status-badge [label]="payment(p).label" [tone]="payment(p).tone" />
                } @else {
                  <span class="muted">—</span>
                }
              </td>
              <td>{{ dateTime(b.createdAt) }}</td>
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="showClient() ? 9 : 8" class="empty">Nenhuma reserva.</td>
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
  `,
})
export class BookingsTable {
  readonly rows = input.required<readonly AdminBookingSummary[]>();
  readonly showClient = input(true);
  readonly caption = input('Reservas');

  protected readonly money = money;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly short = shortId;
  protected status(b: AdminBookingSummary) {
    return BOOKING_STATUS[b.status];
  }
  protected payment(p: NonNullable<AdminBookingSummary['latestPaymentStatus']>) {
    return PAYMENT_STATUS[p];
  }
}
