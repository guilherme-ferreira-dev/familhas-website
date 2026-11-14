import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminApi } from '../../core/admin/admin-api';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';

/** Margem e taxa de serviço (pricing_settings). Vale para buscas e cotações novas. */
@Component({
  selector: 'app-pricing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LoadError],
  template: `
    <div class="page-header">
      <div>
        <h2>Margem e taxa de serviço</h2>
        <p>
          Valem para buscas e cotações novas (em até 30 s em todas as instâncias da API). Cotações e
          reservas que já existem guardam o preço calculado na hora.
        </p>
      </div>
    </div>
    @if (settings.error()) {
      <app-load-error [error]="settings.error()" (retry)="settings.reload()" />
    }
    @if (settings.value(); as s) {
      <p class="muted">
        @if (s.source === 'DATABASE') {
          Salvo por {{ s.updatedBy ?? '—' }} em {{ dateTime(s.updatedAt) }}.
        } @else {
          Ainda não salvo pelo Admin: valem as variáveis de ambiente da API.
        }
      </p>
    }
    <form class="card form-grid" [formGroup]="form" (ngSubmit)="save()">
      <div class="field">
        <label for="margem">Margem (%)</label>
        <input
          id="margem"
          type="number"
          min="0"
          max="100"
          step="0.01"
          formControlName="marginPercent"
        />
      </div>
      <div class="field">
        <label for="taxa">Taxa de serviço por passageiro</label>
        <input
          id="taxa"
          type="number"
          min="0"
          step="0.01"
          formControlName="serviceFeePerPassenger"
        />
      </div>
      <div class="field">
        <label for="moeda">Moeda da taxa</label>
        <input id="moeda" maxlength="3" formControlName="serviceFeeCurrency" />
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
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-3);
    }
    .full {
      grid-column: 1 / -1;
    }
    p {
      max-width: 60rem;
      margin: 0;
    }
  `,
})
export class PricingPage {
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);

  protected readonly saving = signal(false);
  protected readonly dateTime = formatDateTime;
  protected readonly settings = rxResource({ stream: () => this.api.pricingSettings() });
  protected readonly form = inject(FormBuilder).nonNullable.group({
    marginPercent: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    serviceFeePerPassenger: [0, [Validators.required, Validators.min(0)]],
    serviceFeeCurrency: ['BRL', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
  });

  constructor() {
    effect(() => {
      const s = this.settings.value();
      if (s) {
        this.form.reset({
          marginPercent: s.marginPercent,
          serviceFeePerPassenger: s.serviceFeePerPassenger,
          serviceFeeCurrency: s.serviceFeeCurrency,
        });
      }
    });
  }

  protected save(): void {
    const v = this.form.getRawValue();
    if (
      !confirm(
        `Aplicar margem de ${v.marginPercent}% e taxa de ${v.serviceFeePerPassenger} por passageiro às próximas cotações?`,
      )
    ) {
      return;
    }
    this.saving.set(true);
    this.api
      .updatePricingSettings({
        marginPercent: Number(v.marginPercent),
        serviceFeePerPassenger: Number(v.serviceFeePerPassenger),
        serviceFeeCurrency: v.serviceFeeCurrency.toUpperCase(),
      })
      .subscribe({
        next: (saved) => {
          this.settings.set(saved);
          this.saving.set(false);
          this.notifications.info('Margem e taxa salvas.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.notifications.error(errorMessage(error));
        },
      });
  }
}
