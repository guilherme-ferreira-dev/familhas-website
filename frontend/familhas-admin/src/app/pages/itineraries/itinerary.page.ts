import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { CrmFile, Itinerary, ItineraryDay, ItineraryRequest } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { whatsappLink } from '../../shared/crm-format';
import { DEFAULT_PAYMENT_TERMS, itineraryWhatsappText } from './itinerary-text';

/** Ajusta a lista de dias à quantidade escolhida, sem perder o que já foi escrito. */
export function resizeDays(days: readonly ItineraryDay[], count: number): ItineraryDay[] {
  const n = Math.min(30, Math.max(1, Math.trunc(count || 1)));
  return Array.from(
    { length: n },
    (_, i) => days.find((d) => d.day === i + 1) ?? { day: i + 1, title: '', description: '' },
  );
}

/**
 * Roteiro (JSON, "Gerar Roteiro"): destino pela cidade do hotel, quatro seções, valor informado com prioridade,
 * observações de até 2.000 caracteres, fotos, alertas internos fora da proposta, avisos do modelo mestre, A4 e
 * WhatsApp. Geração e importação de PDF por IA ficam desligadas sem provedor.
 */
@Component({
  selector: 'app-itinerary-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/roteiros">← Gerar Roteiro</a></p>
      <div class="page-header">
        <h1 id="titulo">{{ id() ? 'Roteiro: ' + destination : 'Novo roteiro' }}</h1>
        @if (saved(); as it) {
          <div class="actions">
            <a
              class="btn btn--primary"
              [routerLink]="['/roteiros', it.id, 'imprimir']"
              target="_blank"
              >Imprimir (A4)</a
            >
            @if (whatsapp(); as link) {
              <a class="btn" [href]="link" target="_blank" rel="noopener noreferrer"
                >Enviar por WhatsApp</a
              >
            }
          </div>
        }
      </div>

      @if (saved()?.warnings?.length) {
        <div class="alert" role="status">
          <strong>Modelo mestre:</strong> revise antes de enviar.
          <ul>
            @for (w of saved()!.warnings; track w) {
              <li>{{ w }}</li>
            }
          </ul>
        </div>
      }
      @if (error(); as message) {
        <p class="alert" role="alert">{{ message }}</p>
      }
      @if (!aiConfigured()) {
        <p class="muted">
          Geração por IA e importação do PDF não configuradas: escreva o roteiro abaixo.
        </p>
      }

      <fieldset class="card">
        <legend>Proposta</legend>
        <div class="form-grid">
          <div class="field">
            <label for="destino">Destino (cidade do hotel) *</label>
            <input id="destino" [(ngModel)]="destination" maxlength="120" />
          </div>
          <div class="field">
            <label for="dias">Dias *</label>
            <input
              id="dias"
              type="number"
              min="1"
              max="30"
              [ngModel]="daysCount"
              (ngModelChange)="setDays($event)"
            />
          </div>
          <div class="field">
            <label for="periodo">Período</label>
            <input
              id="periodo"
              [(ngModel)]="travelPeriod"
              maxlength="120"
              placeholder="29 de Jan a 04 de Fev"
            />
          </div>
          <div class="field">
            <label for="cliente">Cliente</label>
            <input id="cliente" [(ngModel)]="clientName" maxlength="150" />
          </div>
          <div class="field">
            <label for="tel">Telefone (WhatsApp)</label>
            <input id="tel" [(ngModel)]="clientPhone" maxlength="20" inputmode="tel" />
          </div>
          <div class="field">
            <label for="valor">Valor (vale sobre o do PDF)</label>
            <input
              id="valor"
              [(ngModel)]="priceText"
              maxlength="200"
              placeholder="R$ 4.980 para 2 pessoas"
            />
          </div>
          <div class="field field--wide">
            <label for="pagamento">Condições de pagamento</label>
            <input id="pagamento" [(ngModel)]="paymentTerms" maxlength="300" />
          </div>
          <div class="field field--wide">
            <label for="obs">Observações (itens obrigatórios do roteiro, até 2.000)</label>
            <textarea id="obs" rows="3" maxlength="2000" [(ngModel)]="observations"></textarea>
          </div>
        </div>
      </fieldset>

      <fieldset class="card">
        <legend>1. Sobre o destino</legend>
        <textarea
          aria-label="Sobre o destino"
          rows="3"
          maxlength="1500"
          [(ngModel)]="overview"
        ></textarea>
      </fieldset>

      <fieldset class="card">
        <legend>2. Roteiro dia a dia</legend>
        @for (d of days(); track d.day) {
          <div class="form-grid day">
            <div class="field">
              <label [for]="'dia-' + d.day">Dia {{ d.day }}: título</label>
              <input [id]="'dia-' + d.day" [(ngModel)]="d.title" maxlength="120" />
            </div>
            <div class="field field--wide">
              <label [for]="'desc-' + d.day">Descrição</label>
              <textarea
                [id]="'desc-' + d.day"
                rows="2"
                maxlength="800"
                [(ngModel)]="d.description"
              ></textarea>
            </div>
          </div>
        }
      </fieldset>

      <fieldset class="card">
        <legend>3. O que está incluso (só itens confirmados, um por linha)</legend>
        <textarea aria-label="Itens inclusos" rows="4" [(ngModel)]="included"></textarea>
      </fieldset>

      <fieldset class="card">
        <legend>4. Investimento</legend>
        <textarea
          aria-label="Notas do investimento"
          rows="2"
          maxlength="600"
          [(ngModel)]="investmentNotes"
        ></textarea>
      </fieldset>

      <fieldset class="card">
        <legend>Fotos (até 4)</legend>
        <ul class="plain">
          @for (p of photos(); track p.id) {
            <li>
              {{ p.name }}
              <button type="button" class="btn btn--small" (click)="removePhoto(p)">Remover</button>
            </li>
          }
        </ul>
        @if (photos().length < 4) {
          <input
            aria-label="Adicionar foto"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            [disabled]="busy()"
            (change)="addPhoto($event)"
          />
        }
      </fieldset>

      <fieldset class="card internal">
        <legend>Alertas internos (nunca vão para o cliente)</legend>
        <textarea
          aria-label="Alertas internos"
          rows="2"
          maxlength="2000"
          [(ngModel)]="internalAlerts"
        ></textarea>
      </fieldset>

      <div class="actions">
        <button type="button" class="btn btn--primary" [disabled]="busy()" (click)="save()">
          Salvar
        </button>
        @if (saved() && session.can('crm:roteiros:excluir')) {
          <button type="button" class="btn btn--danger" [disabled]="busy()" (click)="remove()">
            Excluir
          </button>
        }
      </div>
    </section>
  `,
  styles: `
    fieldset.card {
      margin: 0 0 var(--space-3);
      display: grid;
      gap: var(--space-2);
    }
    legend {
      font-weight: 700;
    }
    fieldset textarea {
      width: 100%;
    }
    .internal {
      border-style: dashed;
    }
    .plain {
      list-style: none;
      padding: 0;
      margin: 0;
    }
  `,
})
export class ItineraryPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  readonly id = input<string>('');
  protected readonly saved = signal<Itinerary | null>(null);
  protected readonly days = signal<ItineraryDay[]>(resizeDays([], 5));
  protected readonly photos = signal<CrmFile[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aiConfigured = computed(
    () => this.session.integrations().find((i) => i.key === 'AI_TEXT')?.configured ?? false,
  );
  protected readonly whatsapp = computed(() => {
    const it = this.saved();
    return it
      ? whatsappLink(it.clientPhone, itineraryWhatsappText(it, this.session.company()))
      : null;
  });

  protected destination = '';
  protected daysCount = 5;
  protected travelPeriod = '';
  protected clientName = '';
  protected clientPhone = '';
  protected priceText = '';
  protected paymentTerms = DEFAULT_PAYMENT_TERMS;
  protected observations = '';
  protected overview = '';
  protected included = '';
  protected investmentNotes = '';
  protected internalAlerts = '';

  constructor() {
    void this.session.ready();
    effect(() => {
      const id = this.id();
      if (id) void this.load(id);
    });
  }

  protected setDays(value: number): void {
    this.daysCount = Math.min(30, Math.max(1, Math.trunc(Number(value) || 1)));
    this.days.set(resizeDays(this.days(), this.daysCount));
  }

  protected async addPhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    await this.run(async () => {
      const stored = await firstValueFrom(this.api.uploadFile('ITINERARY_PHOTO', file));
      this.photos.update((list) => [...list, stored]);
    });
    input.value = '';
  }

  protected removePhoto(photo: CrmFile): void {
    this.photos.update((list) => list.filter((p) => p.id !== photo.id));
  }

  request(): ItineraryRequest {
    return {
      destination: this.destination.trim(),
      days: this.daysCount,
      clientName: this.clientName.trim() || null,
      clientPhone: this.clientPhone.trim() || null,
      travelPeriod: this.travelPeriod.trim() || null,
      priceText: this.priceText.trim() || null,
      paymentTerms: this.paymentTerms.trim() || null,
      observations: this.observations.trim() || null,
      internalAlerts: this.internalAlerts.trim() || null,
      sections: {
        overview: this.overview.trim() || null,
        days: this.days().map((d) => ({
          day: d.day,
          title: d.title?.trim() || null,
          description: d.description?.trim() || null,
        })),
        included: this.included
          .split('\n')
          .map((i) => i.trim())
          .filter(Boolean),
        investmentNotes: this.investmentNotes.trim() || null,
      },
      photoIds: this.photos().map((p) => p.id),
      version: this.saved()?.version ?? null,
    };
  }

  protected async save(): Promise<void> {
    this.error.set(null);
    if (!this.destination.trim()) {
      this.error.set('Informe o destino (cidade do hotel).');
      return;
    }
    await this.run(async () => {
      const body = this.request();
      const result = this.id()
        ? await firstValueFrom(this.api.updateItinerary(this.id(), body))
        : await firstValueFrom(this.api.createItinerary(body));
      this.notices.info('Roteiro salvo.');
      if (!this.id()) await this.router.navigate(['/roteiros', result.id]);
      else this.apply(result);
    });
  }

  protected async remove(): Promise<void> {
    if (!confirm('Excluir o roteiro e as fotos?')) return;
    await this.run(async () => {
      await firstValueFrom(this.api.deleteItinerary(this.id()));
      await this.router.navigateByUrl('/roteiros');
    });
  }

  private async load(id: string): Promise<void> {
    try {
      this.apply(await firstValueFrom(this.api.itinerary(id)));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  private apply(it: Itinerary): void {
    this.saved.set(it);
    this.destination = it.destination;
    this.daysCount = it.days;
    this.travelPeriod = it.travelPeriod ?? '';
    this.clientName = it.clientName ?? '';
    this.clientPhone = it.clientPhone ?? '';
    this.priceText = it.priceText ?? '';
    this.paymentTerms = it.paymentTerms ?? DEFAULT_PAYMENT_TERMS;
    this.observations = it.observations ?? '';
    this.overview = it.sections.overview ?? '';
    this.included = (it.sections.included ?? []).join('\n');
    this.investmentNotes = it.sections.investmentNotes ?? '';
    this.internalAlerts = it.internalAlerts ?? '';
    this.days.set(
      resizeDays(it.sections.days ?? [], it.days).map((d) => ({
        ...d,
        title: d.title ?? '',
        description: d.description ?? '',
      })),
    );
    this.photos.set(it.photos);
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
