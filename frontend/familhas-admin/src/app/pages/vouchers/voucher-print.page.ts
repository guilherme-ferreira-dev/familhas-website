import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CrmApi } from '../../core/crm/crm-api';
import { VOUCHER_STATUS, VOUCHER_TYPE } from '../../shared/crm-format';
import { formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';
import { voucherRows } from './voucher-detail.page';

/**
 * Voucher em A4, layout contratual próprio, fora do layout do CRM: cabeçalho fixo da EEBPG (razão social, CNPJ,
 * telefone, Instagram, e-mail), cliente com CPF, serviço, observações e termos. Abrir esta tela grava DOWNLOAD na
 * auditoria (o documento traz o CPF inteiro). Abre o diálogo de impressão quando carrega.
 */
@Component({
  selector: 'app-voucher-print-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError],
  template: `
    @if (doc.error()) {
      <main class="screen-only">
        <app-load-error [error]="doc.error()" (retry)="doc.reload()" />
      </main>
    }
    @if (doc.value(); as d) {
      <article class="sheet" aria-label="Voucher para impressão">
        <header class="head">
          <div>
            <p class="brand"><span class="voe">Voe</span> Familhas</p>
            <p class="legal">{{ d.company.legalName }} · CNPJ {{ d.company.cnpj }}</p>
          </div>
          <ul class="contacts">
            <li>WhatsApp {{ d.company.whatsapp }}</li>
            @if (d.company.instagram) {
              <li>Instagram {{ d.company.instagram }}</li>
            }
            @if (d.company.email) {
              <li>{{ d.company.email }}</li>
            }
            <li>{{ d.company.site }}</li>
          </ul>
        </header>

        <h1>
          Voucher de {{ typeLabel() }} <span class="number">{{ d.voucher.number }}</span>
        </h1>
        @if (d.voucher.status === 'CANCELLED') {
          <p class="cancelled">{{ statusLabel() }}: este voucher não é válido.</p>
        }

        <section>
          <h2>Cliente</h2>
          <dl>
            <dt>Nome</dt>
            <dd>{{ d.voucher.clientName }}</dd>
            <dt>CPF</dt>
            <dd>{{ d.clientCpfFormatted ?? 'não informado' }}</dd>
            <dt>Telefone</dt>
            <dd>{{ d.voucher.clientPhone ?? '—' }}</dd>
          </dl>
        </section>

        <section>
          <h2>Serviço</h2>
          <dl>
            @for (row of rows(); track row.label) {
              <dt>{{ row.label }}</dt>
              <dd>{{ row.value }}</dd>
            }
          </dl>
        </section>

        @if (d.voucher.notes) {
          <section>
            <h2>Observações</h2>
            <p class="pre">{{ d.voucher.notes }}</p>
          </section>
        }

        <footer>
          <p>
            Apresente este voucher ao fornecedor junto com um documento oficial com foto. Horários e
            condições seguem as regras do fornecedor do serviço. Em caso de dúvida, fale com a
            {{ d.company.tradeName }} pelo WhatsApp {{ d.company.whatsapp }}.
          </p>
          <p class="meta">
            Emitido em {{ issued() }} por {{ d.voucher.issuedBy ?? '—' }} ·
            {{ d.company.tradeName }}
          </p>
        </footer>
      </article>
      <p class="screen-only actions">
        <button type="button" class="btn btn--primary" (click)="print()">Imprimir</button>
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
      background: #fff;
      color: #111;
    }
    .sheet {
      box-sizing: border-box;
      width: 210mm;
      min-height: 297mm;
      margin: 0 auto;
      padding: 14mm 16mm;
      font-size: 11pt;
      line-height: 1.4;
    }
    .head {
      display: flex;
      justify-content: space-between;
      gap: 8mm;
      border-bottom: 2px solid var(--color-night);
      padding-bottom: 4mm;
      margin-bottom: 6mm;
    }
    .brand {
      margin: 0;
      font-size: 20pt;
      font-weight: 800;
      color: var(--color-night);
    }
    .voe {
      color: #b07d00;
    }
    .legal {
      margin: 0;
      font-size: 9pt;
    }
    .contacts {
      list-style: none;
      margin: 0;
      padding: 0;
      font-size: 9pt;
      text-align: right;
    }
    h1 {
      font-size: 15pt;
      margin: 0 0 4mm;
    }
    .number {
      font-family: monospace;
      font-weight: 400;
    }
    h2 {
      font-size: 11pt;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin: 5mm 0 2mm;
      border-bottom: 1px solid #ccc;
    }
    dl {
      display: grid;
      grid-template-columns: 45mm 1fr;
      gap: 1mm 4mm;
      margin: 0;
    }
    dt {
      font-weight: 600;
    }
    dd {
      margin: 0;
      white-space: pre-wrap;
    }
    .pre {
      white-space: pre-wrap;
      margin: 0;
    }
    .cancelled {
      border: 2px solid #b42318;
      color: #b42318;
      padding: 2mm 4mm;
      font-weight: 700;
    }
    footer {
      margin-top: 10mm;
      font-size: 9pt;
      border-top: 1px solid #ccc;
      padding-top: 3mm;
    }
    .meta {
      color: #555;
    }
    .actions {
      text-align: center;
      padding: var(--space-3);
    }
    @media screen and (max-width: 220mm) {
      .sheet {
        width: auto;
        min-height: 0;
      }
    }
    @media print {
      .screen-only {
        display: none;
      }
      .sheet {
        width: auto;
        min-height: 0;
        padding: 0;
      }
    }
  `,
})
export class VoucherPrintPage {
  private readonly api = inject(CrmApi);
  readonly id = input.required<string>();

  protected readonly doc = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.voucherDocument(params),
  });
  protected readonly rows = computed(() => {
    const d = this.doc.value();
    return d ? voucherRows(d.voucher) : [];
  });
  protected readonly typeLabel = computed(() => {
    const d = this.doc.value();
    return d ? VOUCHER_TYPE[d.voucher.type].toLowerCase() : '';
  });
  protected readonly statusLabel = computed(() => {
    const d = this.doc.value();
    return d ? VOUCHER_STATUS[d.voucher.status] : '';
  });
  protected readonly issued = computed(() => formatDateTime(this.doc.value()?.voucher.issuedAt));

  constructor() {
    let printed = false;
    effect(() => {
      if (this.doc.value() && !printed) {
        printed = true;
        setTimeout(() => this.print(), 300);
      }
    });
  }

  protected print(): void {
    window.print();
  }
}
