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
  brl,
  DEFAULT_OBSERVATION,
  DEFAULT_PAYMENT,
  formatDateRange,
  inclusionLines,
  perPersonLabel,
  pricePerPerson,
  quoteCaption,
} from '../../shared/marketing/quote-text';
import {
  drawQuotePost,
  POST_SIZE,
  QUOTE_LAYOUTS,
  QuoteLayoutId,
} from '../../shared/marketing/quote-layouts';
import { sendToInstagramQueue } from './publish-to-queue';

/**
 * Criar Posts de cotação (JSON): destino pela cidade da hospedagem, "Carro Alugado? S/N" e "Ideia" abaixo do
 * destino, valor dividido por pessoa (padrão 2, "Valor por pessoa em apto duplo"), pagamento padrão centralizado sob
 * o valor, observação padrão "Saída de Brasília-DF", 5 layouts sem seletor de cor, logo oficial composta, PNG e
 * envio para a fila do Instagram. A prévia acompanha a rolagem.
 */
@Component({
  selector: 'app-quote-post-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Criar Posts de cotação</h1>
          <p>Monte o post a partir do orçamento. A logo oficial entra sozinha.</p>
        </div>
      </div>
      @if (!brand.logoFile()) {
        <p class="alert alert--info" role="status">
          A logo oficial ainda não foi enviada (Visão geral → Agência). Até lá, o post usa a marca
          em texto.
        </p>
      }
      <div class="editor">
        <form
          class="card stack"
          (input)="redraw()"
          (change)="redraw()"
          (submit)="$event.preventDefault()"
        >
          @if (!aiText()) {
            <p class="muted">
              Leitura do PDF por IA não configurada: preencha os dados do orçamento abaixo.
            </p>
          }
          <div class="field">
            <label for="destino">Destino (cidade da hospedagem) *</label>
            <input id="destino" name="destino" [(ngModel)]="f.destination" maxlength="60" />
          </div>
          <fieldset class="field">
            <legend>Carro Alugado?</legend>
            <label class="choice"
              ><input type="radio" name="carro" [value]="false" [(ngModel)]="f.rentedCar" />
              Não</label
            >
            <label class="choice"
              ><input type="radio" name="carro" [value]="true" [(ngModel)]="f.rentedCar" />
              Sim</label
            >
          </fieldset>
          <div class="field">
            <label for="ideia">Ideia (opcional, ex.: dia dos namorados)</label>
            <input id="ideia" name="ideia" [(ngModel)]="f.idea" maxlength="120" />
            <small class="muted">{{
              aiText()
                ? 'A IA usa a ideia para criar um modelo próprio, sem mudar preços e datas.'
                : 'Sem IA configurada, a ideia vira a chamada do post.'
            }}</small>
          </div>
          <div class="form-grid">
            <div class="field">
              <label for="ida">Data de início</label>
              <input id="ida" name="ida" type="date" [(ngModel)]="f.start" />
            </div>
            <div class="field">
              <label for="fim">Data de fim</label>
              <input id="fim" name="fim" type="date" [(ngModel)]="f.end" />
            </div>
            <div class="field">
              <label for="hotel">Hotel</label>
              <input id="hotel" name="hotel" [(ngModel)]="f.hotel" maxlength="80" />
            </div>
            <div class="field">
              <label for="regime">Regime</label>
              <input
                id="regime"
                name="regime"
                [(ngModel)]="f.board"
                maxlength="40"
                placeholder="Café da manhã"
              />
            </div>
          </div>
          <div class="actions">
            <label class="choice"
              ><input type="checkbox" name="aereo" [(ngModel)]="f.flight" /> Aéreo incluso</label
            >
            <label class="choice"
              ><input
                type="checkbox"
                name="traslado"
                [(ngModel)]="f.transfer"
                [disabled]="f.rentedCar"
              />
              Traslado incluso</label
            >
          </div>
          <div class="field">
            <label for="extras"
              >Complementos (ingressos, passeios, seguro viagem; um por linha)</label
            >
            <textarea id="extras" name="extras" rows="3" [(ngModel)]="f.extras"></textarea>
          </div>
          <div class="form-grid">
            <div class="field">
              <label for="total">Valor total do pacote (R$)</label>
              <input
                id="total"
                name="total"
                type="number"
                min="0"
                step="0.01"
                [(ngModel)]="f.total"
              />
            </div>
            <div class="field">
              <label for="pessoas">Pessoas (1 a 10)</label>
              <input
                id="pessoas"
                name="pessoas"
                type="number"
                min="1"
                max="10"
                [(ngModel)]="f.people"
              />
            </div>
          </div>
          <p class="muted" aria-live="polite">{{ priceSummary() }}</p>
          <div class="field">
            <label for="pagamento">Texto de pagamento</label>
            <input id="pagamento" name="pagamento" [(ngModel)]="f.payment" maxlength="60" />
          </div>
          <div class="field">
            <label for="obs">Observação</label>
            <input id="obs" name="obs" [(ngModel)]="f.observation" maxlength="80" />
          </div>
          <div class="field">
            <label for="foto">Foto da cidade da hospedagem</label>
            <input
              id="foto"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              (change)="pickImage($event)"
            />
            @if (!aiImage()) {
              <small class="muted">Imagem por IA não configurada: envie uma foto.</small>
            }
          </div>
          <fieldset class="field">
            <legend>Modelo</legend>
            <div class="layouts">
              @for (l of layouts; track l.id) {
                <label class="choice">
                  <input type="radio" name="layout" [value]="l.id" [(ngModel)]="f.layout" />
                  {{ l.label }}
                </label>
              }
            </div>
          </fieldset>
        </form>

        <aside class="preview" aria-label="Prévia do post">
          <canvas
            #canvas
            [width]="size"
            [height]="size"
            role="img"
            [attr.aria-label]="'Prévia: ' + (f.destination || 'post')"
          ></canvas>
          <div class="actions">
            <button type="button" class="btn btn--primary" (click)="downloadPng()">
              Baixar PNG
            </button>
            @if (session.can('crm:posts-insta:criar')) {
              <button
                type="button"
                class="btn"
                [disabled]="busy() || !f.destination"
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
      height: auto;
      border-radius: var(--radius);
      box-shadow: var(--shadow);
    }
  `,
})
export class QuotePostPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);
  protected readonly brand = inject(BrandStore);

  protected readonly size = POST_SIZE;
  protected readonly layouts = QUOTE_LAYOUTS;
  protected readonly busy = signal(false);
  private readonly version = signal(0);
  private readonly image = signal<HTMLImageElement | null>(null);
  private readonly logo = signal<HTMLImageElement | null>(null);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  protected readonly aiText = computed(() => this.configured('AI_TEXT'));
  protected readonly aiImage = computed(() => this.configured('AI_IMAGE'));

  protected f = {
    destination: '',
    rentedCar: false,
    idea: '',
    start: '',
    end: '',
    hotel: '',
    board: '',
    flight: true,
    transfer: true,
    extras: '',
    total: null as number | null,
    people: 2,
    payment: DEFAULT_PAYMENT,
    observation: DEFAULT_OBSERVATION,
    layout: 'classico' as QuoteLayoutId,
  };

  constructor() {
    void this.session.ready();
    void this.brand.logoImage().then((img) => this.logo.set(img));
    effect(() => {
      this.version();
      this.image();
      this.logo();
      this.draw();
    });
  }

  protected redraw(): void {
    this.version.update((v) => v + 1);
  }

  protected priceSummary(): string {
    this.version();
    const value = pricePerPerson(this.f.total, this.f.people);
    return value === null
      ? 'Informe o valor total para calcular por pessoa.'
      : `${perPersonLabel(this.f.people)}: ${brl(value)}`;
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
      const lines = this.lines();
      const post = await sendToInstagramQueue(this.api, this.canvas().nativeElement, {
        format: 'FEED',
        origin: 'QUOTE_POST',
        title: `${this.f.destination} ${formatDateRange(this.f.start, this.f.end)}`.trim(),
        caption: quoteCaption({
          destination: this.f.destination,
          dates: formatDateRange(this.f.start, this.f.end),
          perPerson: pricePerPerson(this.f.total, this.f.people),
          people: this.f.people,
          lines,
          payment: this.f.payment,
          observation: this.f.observation,
          whatsapp: this.session.company()?.whatsapp ?? '',
        }),
        hashtags: `#voefamilhas #${slug(this.f.destination).replace(/-/g, '')} #viagem`,
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

  private lines(): string[] {
    return inclusionLines({
      flight: this.f.flight,
      hotel: this.f.hotel.trim() || null,
      board: this.f.board.trim() || null,
      transfer: this.f.transfer,
      rentedCar: this.f.rentedCar,
      extras: this.f.extras.split('\n'),
    });
  }

  private draw(): void {
    const ctx = this.canvas().nativeElement.getContext('2d');
    if (!ctx) return;
    const perPerson = pricePerPerson(this.f.total, this.f.people);
    drawQuotePost(ctx, this.f.layout, {
      destination: this.f.destination || 'Destino',
      dates: formatDateRange(this.f.start, this.f.end),
      lines: this.lines(),
      price: perPerson === null ? '' : brl(perPerson),
      priceLabel: perPersonLabel(this.f.people),
      payment: this.f.payment,
      observation: this.f.observation || DEFAULT_OBSERVATION,
      headline: this.f.idea.trim(),
      image: this.image(),
      logo: this.logo(),
    });
  }

  private fileName(): string {
    return slug(`${this.f.destination} ${formatDateRange(this.f.start, this.f.end)}`);
  }

  private configured(key: string): boolean {
    return this.session.integrations().find((i) => i.key === key)?.configured ?? false;
  }
}
