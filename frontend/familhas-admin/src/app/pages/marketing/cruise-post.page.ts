import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BrandStore } from '../../core/crm/brand-store';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { download, fileToImage, slug, toBlob } from '../../shared/marketing/canvas';
import {
  CRUISE_LAYOUTS,
  CruiseFormat,
  CruiseLayoutId,
  cruiseSize,
  drawCruisePost,
} from '../../shared/marketing/cruise-layouts';
import { brl, formatDateRange } from '../../shared/marketing/quote-text';
import { sendToInstagramQueue } from './publish-to-queue';

/** Legenda do cruzeiro (modelo fixo, sem IA). */
export function cruiseCaption(input: {
  ship: string;
  itinerary: string;
  dates: string;
  cabin: string;
  price: number | null;
  installments: string;
  inclusions: readonly string[];
  whatsapp: string;
}): string {
  const lines = [
    `🚢 ${input.ship}`,
    input.itinerary,
    input.dates,
    input.cabin ? `Cabine ${input.cabin}` : '',
    '',
  ];
  lines.push(...input.inclusions.map((i) => `✅ ${i}`));
  if (input.price !== null) lines.push('', `💰 ${brl(input.price)} por pessoa`, input.installments);
  lines.push('', `Chame no WhatsApp ${input.whatsapp} e garanta a sua cabine!`);
  return lines
    .filter((l, i, all) => !(l === '' && all[i - 1] === ''))
    .join('\n')
    .trim();
}

/**
 * Posts de Cruzeiros (JSON): no estilo dos posts de cotação; navio, roteiro, datas, cabine, preço por pessoa e
 * parcelamento; layouts "Card no oceano" e "Oportunidade" em Feed e Story; chamada vazia por padrão.
 */
@Component({
  selector: 'app-cruise-post-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Posts de Cruzeiros</h1>
          <p>Navio, roteiro, cabine e preço por pessoa, com a logo oficial.</p>
        </div>
      </div>
      <div class="editor">
        <form
          class="card stack"
          (input)="redraw()"
          (change)="redraw()"
          (submit)="$event.preventDefault()"
        >
          @if (!aiText()) {
            <p class="muted">
              Importação de PDF ou link por IA não configurada: preencha os dados do orçamento.
            </p>
          }
          <div class="field">
            <label for="chamada">Chamada (opcional)</label>
            <input id="chamada" name="chamada" [(ngModel)]="f.headline" maxlength="40" />
          </div>
          <div class="field">
            <label for="navio">Navio *</label>
            <input id="navio" name="navio" [(ngModel)]="f.ship" maxlength="60" />
          </div>
          <div class="field">
            <label for="roteiro">Roteiro</label>
            <input
              id="roteiro"
              name="roteiro"
              [(ngModel)]="f.itinerary"
              maxlength="160"
              placeholder="Santos · Búzios · Salvador · Santos"
            />
          </div>
          <div class="form-grid">
            <div class="field">
              <label for="embarque">Embarque</label>
              <input id="embarque" name="embarque" type="date" [(ngModel)]="f.start" />
            </div>
            <div class="field">
              <label for="desembarque">Desembarque</label>
              <input id="desembarque" name="desembarque" type="date" [(ngModel)]="f.end" />
            </div>
            <div class="field">
              <label for="cabine">Cabine</label>
              <input
                id="cabine"
                name="cabine"
                [(ngModel)]="f.cabin"
                maxlength="40"
                placeholder="Externa com varanda"
              />
            </div>
            <div class="field">
              <label for="preco">Preço por pessoa (R$)</label>
              <input
                id="preco"
                name="preco"
                type="number"
                min="0"
                step="0.01"
                [(ngModel)]="f.price"
              />
            </div>
          </div>
          <div class="field">
            <label for="parcelas">Parcelamento</label>
            <input id="parcelas" name="parcelas" [(ngModel)]="f.installments" maxlength="60" />
          </div>
          <div class="field">
            <label for="inclusos">Inclusos (um por linha)</label>
            <textarea id="inclusos" name="inclusos" rows="3" [(ngModel)]="f.inclusions"></textarea>
          </div>
          <div class="field">
            <label for="foto">Foto do navio</label>
            <input
              id="foto"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              (change)="pickImage($event)"
            />
            @if (!aiImage()) {
              <small class="muted">Imagem do navio por IA não configurada: envie uma foto.</small>
            }
          </div>
          <fieldset class="field">
            <legend>Modelo</legend>
            @for (l of layouts; track l.id) {
              <label class="choice"
                ><input type="radio" name="layout" [value]="l.id" [(ngModel)]="f.layout" />
                {{ l.label }}</label
              >
            }
          </fieldset>
          <fieldset class="field">
            <legend>Formato</legend>
            <label class="choice"
              ><input type="radio" name="formato" value="FEED" [(ngModel)]="f.format" /> Feed
              (1:1)</label
            >
            <label class="choice"
              ><input type="radio" name="formato" value="STORY" [(ngModel)]="f.format" /> Story
              (9:16)</label
            >
          </fieldset>
        </form>
        <aside class="preview" aria-label="Prévia do post">
          <canvas
            #canvas
            [width]="dims().width"
            [height]="dims().height"
            role="img"
            [attr.aria-label]="'Prévia: ' + (f.ship || 'cruzeiro')"
          ></canvas>
          <div class="actions">
            <button type="button" class="btn btn--primary" (click)="downloadPng()">
              Baixar PNG
            </button>
            @if (session.can('crm:posts-insta:criar')) {
              <button
                type="button"
                class="btn"
                [disabled]="busy() || !f.ship"
                (click)="toInstagram()"
              >
                Enviar para a fila do Instagram
              </button>
            }
          </div>
        </aside>
      </div>
    </section>
  `,
  styles: `
    .editor {
      display: grid;
      gap: var(--space-4);
      align-items: start;
    }
    @media (min-width: 64rem) {
      .editor {
        grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      }
    }
    .stack {
      display: grid;
      gap: var(--space-3);
    }
    fieldset.field {
      border: 0;
      padding: 0;
      margin: 0;
    }
    .choice {
      display: inline-flex;
      gap: var(--space-1);
      align-items: center;
      min-height: 2.75rem;
      margin-inline-end: var(--space-3);
    }
    .preview {
      position: sticky;
      top: var(--space-3);
      display: grid;
      gap: var(--space-3);
    }
    canvas {
      width: 100%;
      max-height: 80vh;
      object-fit: contain;
      height: auto;
      border-radius: var(--radius);
      box-shadow: var(--shadow);
    }
  `,
})
export class CruisePostPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);
  private readonly brand = inject(BrandStore);

  protected readonly layouts = CRUISE_LAYOUTS;
  protected readonly busy = signal(false);
  private readonly version = signal(0);
  private readonly image = signal<HTMLImageElement | null>(null);
  private readonly logo = signal<HTMLImageElement | null>(null);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  protected readonly aiText = computed(() => this.configured('AI_TEXT'));
  protected readonly aiImage = computed(() => this.configured('AI_IMAGE'));
  protected readonly dims = computed(() => {
    this.version();
    return cruiseSize(this.f.format);
  });

  protected f = {
    headline: '',
    ship: '',
    itinerary: '',
    start: '',
    end: '',
    cabin: '',
    price: null as number | null,
    installments: 'em até 10x sem juros',
    inclusions: 'Pensão completa a bordo\nTaxas portuárias',
    layout: 'card-oceano' as CruiseLayoutId,
    format: 'FEED' as CruiseFormat,
  };

  constructor() {
    void this.session.ready();
    void this.brand.logoImage().then((img) => this.logo.set(img));
    effect(() => {
      this.version();
      this.image();
      this.logo();
      queueMicrotask(() => this.draw());
    });
  }

  protected redraw(): void {
    this.version.update((v) => v + 1);
  }

  protected async pickImage(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    try {
      this.image.set(await fileToImage(file));
    } catch (error) {
      this.notices.error(errorMessage(error));
    }
  }

  protected async downloadPng(): Promise<void> {
    download(await toBlob(this.canvas().nativeElement, 'image/png'), `${this.fileName()}.png`);
  }

  protected async toInstagram(): Promise<void> {
    this.busy.set(true);
    try {
      const post = await sendToInstagramQueue(this.api, this.canvas().nativeElement, {
        format: this.f.format,
        origin: 'CRUISE_POST',
        title: `${this.f.ship} ${formatDateRange(this.f.start, this.f.end)}`.trim(),
        caption: cruiseCaption({
          ship: this.f.ship,
          itinerary: this.f.itinerary,
          dates: formatDateRange(this.f.start, this.f.end),
          cabin: this.f.cabin,
          price: this.f.price && this.f.price > 0 ? this.f.price : null,
          installments: this.f.installments,
          inclusions: this.inclusions(),
          whatsapp: this.session.company()?.whatsapp ?? '',
        }),
        hashtags: '#voefamilhas #cruzeiro #viagem',
        fileName: this.fileName(),
      });
      this.notices.info('Post enviado para a fila. Revise e marque como pronto para publicar.');
      await this.router.navigate(['/posts-insta', post.id]);
    } catch (error) {
      this.notices.error(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  private inclusions(): string[] {
    return this.f.inclusions
      .split('\n')
      .map((i) => i.trim())
      .filter(Boolean);
  }

  private draw(): void {
    const ctx = this.canvas().nativeElement.getContext('2d');
    if (!ctx) return;
    drawCruisePost(ctx, this.f.layout, this.f.format, {
      headline: this.f.headline.trim(),
      ship: this.f.ship || 'Navio',
      itinerary: this.f.itinerary,
      dates: formatDateRange(this.f.start, this.f.end),
      cabin: this.f.cabin,
      price: this.f.price && this.f.price > 0 ? brl(this.f.price) : '',
      installments: this.f.installments,
      inclusions: this.inclusions(),
      image: this.image(),
      logo: this.logo(),
    });
  }

  private fileName(): string {
    return slug(`cruzeiro ${this.f.ship} ${this.f.format}`);
  }

  private configured(key: string): boolean {
    return this.session.integrations().find((i) => i.key === key)?.configured ?? false;
  }
}
