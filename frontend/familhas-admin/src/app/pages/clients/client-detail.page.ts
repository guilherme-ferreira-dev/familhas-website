import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { Client, EntityStatus } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { BookingsTable } from '../../shared/bookings-table';
import { ENTITY_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { PaymentsTable } from '../../shared/payments-table';
import { StatusBadge } from '../../shared/status-badge';

/** Detalhe do cliente: dados (editáveis), reservas e pagamentos. */
@Component({
  selector: 'app-client-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    BookingsTable,
    PaymentsTable,
    Pager,
    StatusBadge,
    LoadError,
  ],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/clientes">← Clientes</a></p>
      @if (client.error()) {
        <app-load-error [error]="client.error()" (retry)="client.reload()" />
      }
      @if (client.value(); as c) {
        <div class="page-header">
          <div>
            <h1 id="titulo">{{ c.name }}</h1>
            <p>
              {{ c.email }} ·
              <app-status-badge [label]="status(c.status).label" [tone]="status(c.status).tone" /> ·
              {{ c.hasLogin ? 'com login na Loja' : 'sem login (cadastro pelo Admin)' }}
            </p>
          </div>
          <div class="actions">
            <a
              class="btn btn--primary"
              routerLink="/cotacoes/nova"
              [queryParams]="{ cliente: c.id }"
            >
              Nova cotação manual
            </a>
          </div>
        </div>

        <form
          class="card form-grid"
          [formGroup]="form"
          (ngSubmit)="save(c)"
          aria-labelledby="dados"
        >
          <h2 id="dados" class="full">Dados</h2>
          <div class="field">
            <label for="nome">Nome</label>
            <input
              id="nome"
              formControlName="name"
              maxlength="180"
              [attr.aria-invalid]="form.controls.name.invalid"
            />
          </div>
          <div class="field">
            <label for="telefone">Telefone</label>
            <input id="telefone" formControlName="phoneNumber" maxlength="40" />
          </div>
          <div class="field">
            <label for="status">Status</label>
            <select id="status" formControlName="status">
              <option value="ACTIVE">Ativo</option>
              <option value="INACTIVE">Inativo</option>
            </select>
          </div>
          <div class="field">
            <span class="label">E-mail</span>
            <span>{{ c.email }}</span>
            @if (c.hasLogin) {
              <small class="muted">Gerido pelo Keycloak.</small>
            }
          </div>
          <div class="actions full">
            <button
              type="submit"
              class="btn btn--primary"
              [disabled]="form.invalid || form.pristine || saving()"
            >
              {{ saving() ? 'Salvando…' : 'Salvar' }}
            </button>
          </div>
        </form>

        <section aria-labelledby="reservas" class="stack">
          <h2 id="reservas">Reservas</h2>
          @if (bookings.error()) {
            <app-load-error [error]="bookings.error()" (retry)="bookings.reload()" />
          }
          <app-bookings-table
            [rows]="bookings.value()?.content ?? []"
            [showClient]="false"
            caption="Reservas do cliente"
          />
          <app-pager [meta]="bookings.value() ?? null" (pageChange)="bookingsPage.set($event)" />
        </section>

        <section aria-labelledby="pagamentos" class="stack">
          <h2 id="pagamentos">Pagamentos</h2>
          @if (payments.error()) {
            <app-load-error [error]="payments.error()" (retry)="payments.reload()" />
          }
          <app-payments-table
            [rows]="payments.value()?.content ?? []"
            [showClient]="false"
            caption="Pagamentos do cliente"
          />
          <app-pager [meta]="payments.value() ?? null" (pageChange)="paymentsPage.set($event)" />
        </section>
      } @else if (client.isLoading()) {
        <p class="muted" role="status">Carregando cliente…</p>
      }
    </section>
  `,
  styles: `
    .full {
      grid-column: 1 / -1;
    }
    .stack {
      display: grid;
      gap: var(--space-2);
    }
  `,
})
export class ClientDetailPage {
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);

  readonly id = input.required<string>();

  protected readonly saving = signal(false);
  protected readonly bookingsPage = signal(0);
  protected readonly paymentsPage = signal(0);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(180)]],
    phoneNumber: ['', Validators.maxLength(40)],
    status: ['ACTIVE' as EntityStatus, Validators.required],
  });

  protected readonly client = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.client(params),
  });
  protected readonly bookings = rxResource({
    params: () => ({ clientId: this.id(), page: this.bookingsPage() }),
    stream: ({ params }) =>
      this.api.bookings({ clientId: params.clientId, page: params.page, size: 10 }),
  });
  protected readonly payments = rxResource({
    params: () => ({ clientId: this.id(), page: this.paymentsPage() }),
    stream: ({ params }) =>
      this.api.payments(
        { clientId: params.clientId },
        { page: params.page, size: 10, sort: 'createdAt,desc' },
      ),
  });

  constructor() {
    effect(() => {
      const c = this.client.value();
      if (c) {
        this.form.reset({ name: c.name, phoneNumber: c.phoneNumber ?? '', status: c.status });
      }
    });
  }

  protected status(status: EntityStatus) {
    return ENTITY_STATUS[status];
  }

  protected save(c: Client): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .updateClient(c.id, {
        name: value.name.trim(),
        email: c.email,
        phoneNumber: value.phoneNumber.trim() || null,
        status: value.status,
        favoriteIds: null,
        myFlightIds: null,
      })
      .subscribe({
        next: (updated) => {
          this.client.set(updated);
          this.saving.set(false);
          this.notifications.info('Cliente salvo.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.notifications.error(errorMessage(error));
        },
      });
  }
}
