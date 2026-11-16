import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { CrmFile, Voucher } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import {
  VOUCHER_STATUS,
  VOUCHER_TYPE,
  voucherWhatsappText,
  whatsappLink,
} from '../../shared/crm-format';
import { FileViewer } from '../../shared/file-viewer';
import { formatDate, formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';
import { VOUCHER_FIELDS } from './voucher-fields';

/** Voucher emitido: dados, impressão A4, envio por WhatsApp (sem CPF), correção e cancelamento. */
@Component({
  selector: 'app-voucher-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LoadError, FileViewer],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/vouchers">← Vouchers</a></p>
      @if (voucher.error()) {
        <app-load-error [error]="voucher.error()" (retry)="voucher.reload()" />
      }
      @if (voucher.value(); as v) {
        <div class="page-header">
          <div>
            <h1 id="titulo">{{ v.number }} · {{ typeLabel(v) }}</h1>
            <p>
              {{ v.clientName }} · {{ statusLabel(v) }} · emitido {{ dateTime(v.issuedAt) }} por
              {{ v.issuedBy ?? '—' }}
            </p>
          </div>
          <div class="actions">
            <a
              class="btn btn--primary"
              [routerLink]="['/vouchers', v.id, 'imprimir']"
              target="_blank"
              >Imprimir (A4)</a
            >
            @if (whatsapp(); as link) {
              <a class="btn" [href]="link" target="_blank" rel="noopener noreferrer"
                >Enviar por WhatsApp</a
              >
            }
            @if (v.status === 'ISSUED' && session.can('crm:vouchers:editar')) {
              <a class="btn" [routerLink]="['/vouchers', v.id, 'editar']">Corrigir</a>
              <button type="button" class="btn btn--danger" [disabled]="busy()" (click)="cancel(v)">
                Cancelar voucher
              </button>
            }
          </div>
        </div>
        @if (!whatsapp()) {
          <p class="muted">Sem telefone válido do cliente: o envio por WhatsApp fica desligado.</p>
        }

        <div class="grid">
          <section class="card" aria-labelledby="cliente">
            <h2 id="cliente">Cliente</h2>
            <dl class="details">
              <dt>Nome</dt>
              <dd>{{ v.clientName }}</dd>
              <dt>CPF</dt>
              <dd>{{ v.clientCpfMasked ?? 'não informado' }}</dd>
              <dt>Telefone</dt>
              <dd>{{ v.clientPhone ?? '—' }}</dd>
              <dt>Observações</dt>
              <dd class="pre">{{ v.notes ?? '—' }}</dd>
            </dl>
          </section>
          <section class="card" aria-labelledby="servico">
            <h2 id="servico">Serviço</h2>
            <dl class="details">
              @for (row of rows(); track row.label) {
                <dt>{{ row.label }}</dt>
                <dd class="pre">{{ row.value }}</dd>
              }
            </dl>
          </section>
        </div>
        @if (v.sourceFile; as file) {
          <p>
            Documento de origem:
            <button type="button" class="btn btn--small" (click)="viewing.set(file)">
              {{ file.name }}
            </button>
          </p>
        }
        @if (viewing(); as file) {
          <app-file-viewer [file]="file" (closed)="viewing.set(null)" />
        }
      }
    </section>
  `,
  styles: `
    .grid {
      display: grid;
      gap: var(--space-3);
      margin-bottom: var(--space-3);
    }
    @media (min-width: 60rem) {
      .grid {
        grid-template-columns: 1fr 1fr;
      }
    }
    h2 {
      margin: 0 0 var(--space-2);
      font-size: 1.125rem;
    }
    .pre {
      white-space: pre-wrap;
    }
  `,
})
export class VoucherDetailPage {
  private readonly api = inject(CrmApi);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  readonly id = input.required<string>();
  protected readonly busy = signal(false);
  protected readonly viewing = signal<CrmFile | null>(null);
  protected readonly voucher = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.voucher(params),
  });
  protected readonly whatsapp = computed(() => {
    const v = this.voucher.value();
    return v ? whatsappLink(v.clientPhone, voucherWhatsappText(v, this.session.company())) : null;
  });
  protected readonly rows = computed(() =>
    this.voucher.value() ? voucherRows(this.voucher.value()!) : [],
  );

  constructor() {
    void this.session.ready();
  }

  protected typeLabel(v: Voucher): string {
    return VOUCHER_TYPE[v.type];
  }

  protected statusLabel(v: Voucher): string {
    return VOUCHER_STATUS[v.status];
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected async cancel(v: Voucher): Promise<void> {
    if (!confirm(`Cancelar o voucher ${v.number}? Ele continua no histórico.`)) return;
    this.busy.set(true);
    try {
      this.voucher.set(await firstValueFrom(this.api.cancelVoucher(v.id)));
      this.notices.info('Voucher cancelado.');
    } catch (error) {
      this.notices.error(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Linhas preenchidas do serviço, com os rótulos do tipo (também usadas na impressão). */
export function voucherRows(v: Voucher): { label: string; value: string }[] {
  const rows = VOUCHER_FIELDS[v.type]
    .map((f) => {
      const raw = v.details[f.key];
      if (raw === null || raw === undefined || `${raw}`.trim() === '') return null;
      return { label: f.label, value: f.kind === 'date' ? formatDate(`${raw}`) : `${raw}` };
    })
    .filter((row): row is { label: string; value: string } => row !== null);
  if (v.details.passengers?.length) {
    rows.push({ label: 'Outros passageiros', value: v.details.passengers.join('\n') });
  }
  return rows;
}
