import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import type { BookingStatus } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { BookingsTable } from '../../shared/bookings-table';
import { BOOKING_STATUS, options } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';

/** Reservas filtradas por status e período de criação. Estado na URL (?status=&de=&ate=&pagina=). */
@Component({
  selector: 'app-bookings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, BookingsTable, Pager, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Reservas</h1>
          <p>Da mais recente para a mais antiga. O período filtra pela data de criação.</p>
        </div>
      </div>

      <form class="filters" (submit)="apply($event, statusEl.value, fromEl.value, toEl.value)">
        <div class="field">
          <label for="status">Status</label>
          <select id="status" #statusEl [value]="status() ?? ''">
            <option value="">Todos</option>
            @for (o of statusOptions; track o.value) {
              <option [value]="o.value" [selected]="o.value === status()">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="de">Criadas de</label>
          <input id="de" #fromEl type="date" [value]="de() ?? ''" />
        </div>
        <div class="field">
          <label for="ate">até</label>
          <input id="ate" #toEl type="date" [value]="ate() ?? ''" />
        </div>
        <button type="submit" class="btn btn--primary">Filtrar</button>
        <button type="button" class="btn" (click)="clear()">Limpar</button>
      </form>

      @if (bookings.error()) {
        <app-load-error [error]="bookings.error()" (retry)="bookings.reload()" />
      }
      <app-bookings-table [rows]="bookings.value()?.content ?? []" />
      <app-pager [meta]="bookings.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
})
export class BookingsPage {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);

  readonly status = input<BookingStatus | undefined>();
  readonly de = input<string>();
  readonly ate = input<string>();
  readonly cliente = input<string>();
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly statusOptions = options(BOOKING_STATUS);

  protected readonly bookings = rxResource({
    params: () => ({
      status: this.status() || null,
      from: this.de() || null,
      to: this.ate() || null,
      clientId: this.cliente() || null,
      page: this.pagina(),
    }),
    stream: ({ params }) => this.api.bookings({ ...params, size: 20, sort: 'createdAt,desc' }),
  });

  protected apply(event: Event, status: string, de: string, ate: string): void {
    event.preventDefault();
    void this.router.navigate([], {
      queryParams: {
        status: status || null,
        de: de || null,
        ate: ate || null,
        cliente: this.cliente() || null,
      },
    });
  }

  protected clear(): void {
    void this.router.navigate([]);
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
