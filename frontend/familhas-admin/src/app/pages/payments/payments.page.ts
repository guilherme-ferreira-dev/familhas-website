import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { debounceTime, interval, tap } from 'rxjs';
import type { PaymentMethod, PaymentStatus } from '../../core/api/api-types';
import { AdminApi, ExportFormat, PaymentFilter } from '../../core/admin/admin-api';
import { exportFileName, saveDownload } from '../../core/admin/download';
import { PaymentFeed } from '../../core/admin/payment-feed';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { options, PAYMENT_METHOD, PAYMENT_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { PaymentsTable } from '../../shared/payments-table';

const POLL_MS = 30_000;
const STATUSES = Object.keys(PAYMENT_STATUS) as PaymentStatus[];

/**
 * Planilha automática de pagamentos (guia, Fase 10). Filtros na URL. Atualiza sozinha:
 * - na hora, pelos eventos de pagamento da Fase 6 (SSE);
 * - a cada 30 s, para estornos e sessões expiradas, que não geram evento.
 */
@Component({
  selector: 'app-payments-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PaymentsTable, Pager, LoadError],
  providers: [PaymentFeed],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Pagamentos</h1>
          <p>
            <span class="live" [attr.data-status]="feed.status()">{{ liveLabel() }}</span>
            @if (updatedAt(); as t) {
              · atualizado às {{ t }}
            }
          </p>
        </div>
        <div class="actions">
          <button type="button" class="btn" [disabled]="exporting()" (click)="export('xlsx')">
            Exportar XLSX
          </button>
          <button type="button" class="btn" [disabled]="exporting()" (click)="export('csv')">
            Exportar CSV
          </button>
        </div>
      </div>

      <form class="filters card" (submit)="apply($event, form)" #form>
        <div class="field">
          <label for="data">Período por</label>
          <select id="data" name="data">
            <option value="CREATED" [selected]="data() !== 'PAID'">Data de criação</option>
            <option value="PAID" [selected]="data() === 'PAID'">Data do pagamento</option>
          </select>
        </div>
        <div class="field">
          <label for="de">De</label>
          <input id="de" name="de" type="date" [value]="de() ?? ''" />
        </div>
        <div class="field">
          <label for="ate">Até</label>
          <input id="ate" name="ate" type="date" [value]="ate() ?? ''" />
        </div>
        <div class="field">
          <label for="metodo">Método</label>
          <select id="metodo" name="metodo">
            <option value="">Todos</option>
            @for (o of methodOptions; track o.value) {
              <option [value]="o.value" [selected]="o.value === metodo()">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="parcelas">Parcelas</label>
          <input
            id="parcelas"
            name="parcelas"
            type="number"
            min="1"
            max="24"
            [value]="parcelas() ?? ''"
          />
        </div>
        <fieldset class="field field--wide">
          <legend>Status</legend>
          <div class="checks">
            @for (o of statusOptions; track o.value) {
              <label>
                <input
                  type="checkbox"
                  name="status"
                  [value]="o.value"
                  [checked]="selectedStatus().includes(o.value)"
                />
                {{ o.label }}
              </label>
            }
          </div>
        </fieldset>
        <div class="actions">
          <button type="submit" class="btn btn--primary">Filtrar</button>
          <button type="button" class="btn" (click)="clear()">Limpar</button>
        </div>
      </form>

      <p class="visually-hidden" aria-live="polite">{{ announcement() }}</p>

      @if (payments.error()) {
        <app-load-error [error]="payments.error()" (retry)="payments.reload()" />
      }
      <app-payments-table
        [rows]="payments.value()?.content ?? []"
        [highlight]="fresh()"
        caption="Planilha de pagamentos"
      />
      <app-pager [meta]="payments.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
  styles: `
    .live::before {
      content: '';
      display: inline-block;
      width: 0.6rem;
      height: 0.6rem;
      margin-inline-end: 0.35rem;
      border-radius: 50%;
      background: var(--color-muted);
    }
    .live[data-status='live']::before {
      background: var(--color-success);
    }
    .live[data-status='connecting']::before {
      background: var(--color-yellow);
    }
  `,
})
export class PaymentsPage {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  protected readonly feed = inject(PaymentFeed);

  /** Query params. `status` pode repetir. */
  readonly de = input<string>();
  readonly ate = input<string>();
  readonly data = input<string>();
  readonly status = input<string | string[]>();
  readonly metodo = input<string>();
  readonly parcelas = input<string>();
  readonly cliente = input<string>();
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly statusOptions = options(PAYMENT_STATUS);
  protected readonly methodOptions = options(PAYMENT_METHOD);
  protected readonly exporting = signal(false);
  protected readonly fresh = signal<ReadonlySet<string>>(new Set());
  protected readonly announcement = signal('');
  protected readonly updatedAt = signal<string | null>(null);

  protected readonly selectedStatus = computed(() => {
    const raw = this.status();
    const list = raw === undefined ? [] : Array.isArray(raw) ? raw : [raw];
    return list.filter((s): s is PaymentStatus => STATUSES.includes(s as PaymentStatus));
  });

  protected readonly filter = computed<PaymentFilter>(() => {
    const installments = Number(this.parcelas());
    return {
      from: this.de() || null,
      to: this.ate() || null,
      dateField: this.data() === 'PAID' ? 'PAID' : null,
      status: this.selectedStatus(),
      method: (this.metodo() as PaymentMethod) || null,
      installments: Number.isInteger(installments) && installments > 0 ? installments : null,
      clientId: this.cliente() || null,
    };
  });

  protected readonly payments = rxResource({
    params: () => ({ filter: this.filter(), page: this.pagina() }),
    stream: ({ params }) =>
      this.api
        .payments(params.filter, { page: params.page, size: 50, sort: 'createdAt,desc' })
        .pipe(tap(() => this.updatedAt.set(new Date().toLocaleTimeString('pt-BR')))),
  });

  protected readonly liveLabel = computed(() => {
    switch (this.feed.status()) {
      case 'live':
        return 'Tempo real ligado';
      case 'connecting':
        return 'Conectando ao tempo real…';
      default:
        return 'Sem tempo real: atualiza a cada 30 s';
    }
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.feed.start();
    this.feed.events.pipe(takeUntilDestroyed(destroyRef)).subscribe((event) => {
      this.fresh.update((ids) => new Set([...ids, event.paymentId]));
      this.announcement.set(`Pagamento ${event.paymentId.slice(0, 8)} atualizado.`);
      setTimeout(
        () => this.fresh.update((ids) => new Set([...ids].filter((id) => id !== event.paymentId))),
        4000,
      );
    });
    // Vários eventos juntos (ex.: reconciliação) viram uma recarga só.
    this.feed.events
      .pipe(debounceTime(500), takeUntilDestroyed(destroyRef))
      .subscribe(() => this.payments.reload());
    interval(POLL_MS)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.payments.reload());
  }

  protected apply(event: Event, form: HTMLFormElement): void {
    event.preventDefault();
    const data = new FormData(form);
    const text = (name: string) => String(data.get(name) ?? '').trim() || null;
    void this.router.navigate([], {
      queryParams: {
        data: text('data') === 'PAID' ? 'PAID' : null,
        de: text('de'),
        ate: text('ate'),
        metodo: text('metodo'),
        parcelas: text('parcelas'),
        status: data.getAll('status').map(String),
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

  protected export(format: ExportFormat): void {
    this.exporting.set(true);
    this.api.exportPayments(this.filter(), format).subscribe({
      next: (response) => {
        saveDownload(response, exportFileName('pagamentos', format));
        this.exporting.set(false);
      },
      error: (error: unknown) => {
        this.exporting.set(false);
        this.notifications.error(errorMessage(error));
      },
    });
  }
}
