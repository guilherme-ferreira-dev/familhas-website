import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { BrandStore } from '../../core/crm/brand-store';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { LoadError } from '../../shared/load-error';
import { DEFAULT_PAYMENT_TERMS } from './itinerary-text';

/**
 * Proposta em uma página A4 com a identidade da agência (logo, telefone, Instagram) e a seção INVESTIMENTO com as
 * condições de pagamento. Alertas internos nunca aparecem aqui.
 */
@Component({
  selector: 'app-itinerary-print-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError],
  template: `
    @if (doc.error()) {
      <main class="screen-only">
        <app-load-error [error]="doc.error()" (retry)="doc.reload()" />
      </main>
    }
    @if (doc.value(); as it) {
      <article class="sheet" aria-label="Roteiro para impressão">
        <header class="head">
          @if (brand.logoUrl(); as logo) {
            <img class="logo" [src]="logo" alt="Voe Familhas" />
          } @else {
            <p class="brand"><span class="voe">Voe</span> Familhas</p>
          }
          <ul class="contacts">
            @if (session.company(); as c) {
              <li>WhatsApp {{ c.whatsapp }}</li>
              @if (c.instagram) {
                <li>Instagram {{ c.instagram }}</li>
              }
              <li>{{ c.site }}</li>
            }
          </ul>
        </header>
        <h1>{{ it.destination }} · {{ it.days }} {{ it.days === 1 ? 'dia' : 'dias' }}</h1>
        <p class="sub">
          @if (it.clientName) {
            Proposta para {{ it.clientName }}
          }
          @if (it.travelPeriod) {
            · {{ it.travelPeriod }}
          }
        </p>
        @if (photos().length) {
          <div class="photos">
            @for (p of photos(); track p) {
              <img [src]="p" alt="" />
            }
          </div>
        }
        @if (it.sections.overview) {
          <section>
            <h2>O destino</h2>
            <p>{{ it.sections.overview }}</p>
          </section>
        }
        @if (it.sections.days?.length) {
          <section>
            <h2>Roteiro</h2>
            <ol class="days">
              @for (d of it.sections.days; track d.day) {
                <li>
                  <strong>Dia {{ d.day }}{{ d.title ? ' · ' + d.title : '' }}</strong
                  >{{ d.description ? ': ' + d.description : '' }}
                </li>
              }
            </ol>
          </section>
        }
        @if (it.sections.included?.length) {
          <section>
            <h2>O que está incluso</h2>
            <ul class="included">
              @for (i of it.sections.included; track i) {
                <li>{{ i }}</li>
              }
            </ul>
          </section>
        }
        <section class="investment">
          <h2>Investimento</h2>
          @if (it.priceText) {
            <p class="price">{{ it.priceText }}</p>
          }
          <p>{{ it.paymentTerms || defaultTerms }}</p>
          @if (it.sections.investmentNotes) {
            <p>{{ it.sections.investmentNotes }}</p>
          }
        </section>
        @if (it.observations) {
          <section>
            <h2>Observações</h2>
            <p class="pre">{{ it.observations }}</p>
          </section>
        }
        @if (session.company(); as c) {
          <footer>{{ c.legalName }} · CNPJ {{ c.cnpj }}</footer>
        }
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
      height: 297mm;
      overflow: hidden;
      margin: 0 auto;
      padding: 12mm 14mm;
      font-size: 10pt;
      line-height: 1.35;
    }
    .head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 2px solid var(--color-night);
      padding-bottom: 3mm;
    }
    .logo {
      max-height: 18mm;
      max-width: 60mm;
    }
    .brand {
      margin: 0;
      font-size: 18pt;
      font-weight: 800;
      color: var(--color-night);
    }
    .voe {
      color: #b07d00;
    }
    .contacts {
      list-style: none;
      margin: 0;
      padding: 0;
      text-align: right;
      font-size: 9pt;
    }
    h1 {
      margin: 4mm 0 0;
      font-size: 17pt;
    }
    .sub {
      margin: 0 0 3mm;
      color: #444;
    }
    h2 {
      font-size: 10.5pt;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin: 3mm 0 1mm;
      border-bottom: 1px solid #ccc;
    }
    p {
      margin: 0 0 1.5mm;
    }
    .photos {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(30mm, 1fr));
      gap: 2mm;
      margin-bottom: 2mm;
    }
    .photos img {
      width: 100%;
      height: 32mm;
      object-fit: cover;
      border-radius: 2mm;
    }
    .days,
    .included {
      margin: 0;
      padding-inline-start: 5mm;
    }
    .included {
      columns: 2;
    }
    .investment {
      border: 2px solid var(--color-night);
      border-radius: 2mm;
      padding: 1mm 4mm 2mm;
      margin-top: 3mm;
    }
    .price {
      font-size: 14pt;
      font-weight: 800;
    }
    .pre {
      white-space: pre-wrap;
    }
    footer {
      margin-top: 3mm;
      font-size: 8pt;
      color: #555;
      text-align: center;
    }
    .actions {
      text-align: center;
      padding: var(--space-3);
    }
    @media print {
      .screen-only {
        display: none;
      }
      .sheet {
        width: auto;
        height: auto;
        max-height: 273mm;
        padding: 0;
      }
    }
  `,
})
export class ItineraryPrintPage {
  private readonly api = inject(CrmApi);
  protected readonly session = inject(CrmSession);
  protected readonly brand = inject(BrandStore);
  protected readonly defaultTerms = DEFAULT_PAYMENT_TERMS;
  readonly id = input.required<string>();
  protected readonly photos = signal<string[]>([]);
  protected readonly doc = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.itinerary(params),
  });

  constructor() {
    void this.session.ready();
    void this.brand.ready();
    let printed = false;
    effect(() => {
      const it = this.doc.value();
      if (!it || printed) return;
      printed = true;
      void Promise.all(
        it.photos.map(async (p) =>
          this.api.absolute((await firstValueFrom(this.api.fileLink(p.id))).path),
        ),
      )
        .then((urls) => this.photos.set(urls))
        .finally(() => setTimeout(() => this.print(), 600));
    });
  }

  protected print(): void {
    window.print();
  }
}
