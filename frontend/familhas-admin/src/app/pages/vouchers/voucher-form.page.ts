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
import type {
  CrmFile,
  VoucherDetails,
  VoucherRequest,
  VoucherType,
} from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { VOUCHER_TYPE } from '../../shared/crm-format';
import { DetailField, missingFields, VOUCHER_FIELDS } from './voucher-fields';

/**
 * Emissão e correção de voucher. O PDF/imagem de origem fica guardado (privado); a extração por IA só aparece
 * ligada com provedor configurado. NOME, CPF, TELEFONE e OBSERVAÇÕES são sempre digitados.
 */
@Component({
  selector: 'app-voucher-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a [routerLink]="id() ? ['/vouchers', id()] : '/vouchers'">← Voltar</a></p>
      <div class="page-header">
        <h1 id="titulo">{{ id() ? 'Corrigir voucher' : 'Emitir voucher' }}</h1>
      </div>

      @if (error(); as message) {
        <p class="alert" role="alert">{{ message }}</p>
      }

      <form (submit)="submit($event)" novalidate>
        <fieldset class="card">
          <legend>Tipo</legend>
          <div class="actions" role="radiogroup" aria-label="Tipo do voucher">
            @for (t of types; track t) {
              <label class="choice">
                <input
                  type="radio"
                  name="tipo"
                  [value]="t"
                  [checked]="type() === t"
                  (change)="type.set(t)"
                />
                {{ typeLabel(t) }}
              </label>
            }
          </div>
        </fieldset>

        <fieldset class="card">
          <legend>Documento de origem (opcional)</legend>
          @if (source(); as file) {
            <p>
              Enviado: <strong>{{ file.name }}</strong>
              <button type="button" class="btn btn--small" (click)="source.set(null)">
                Trocar
              </button>
            </p>
          } @else {
            <div class="field">
              <label for="origem">PDF, JPG ou PNG do fornecedor</label>
              <input
                id="origem"
                type="file"
                accept="application/pdf,image/jpeg,image/png"
                [disabled]="uploading()"
                (change)="upload($event)"
              />
            </div>
          }
          @if (aiConfigured()) {
            <button
              type="button"
              class="btn"
              [disabled]="!source() || extracting()"
              (click)="extract()"
            >
              {{ extracting() ? 'Lendo o documento…' : 'Extrair dados com IA' }}
            </button>
            <p class="muted">
              Revise tudo antes de emitir. Nome, CPF e telefone nunca são lidos pela IA.
            </p>
          } @else {
            <p class="alert alert--info" role="status">
              A extração por IA não está configurada. Preencha os dados do serviço abaixo.
            </p>
          }
        </fieldset>

        <fieldset class="card">
          <legend>Dados do cliente (sempre preenchidos à mão)</legend>
          <div class="form-grid">
            <div class="field">
              <label for="nome">Nome *</label>
              <input id="nome" name="nome" [(ngModel)]="clientName" maxlength="150" required />
            </div>
            <div class="field">
              <label for="cpf">CPF</label>
              <input
                id="cpf"
                name="cpf"
                [(ngModel)]="clientCpf"
                maxlength="14"
                inputmode="numeric"
                autocomplete="off"
                [placeholder]="cpfMasked() ?? '000.000.000-00'"
              />
              @if (cpfMasked()) {
                <small class="muted"
                  >Guardado: {{ cpfMasked() }}. Deixe em branco para manter.</small
                >
              }
            </div>
            <div class="field">
              <label for="tel">Telefone (WhatsApp)</label>
              <input id="tel" name="tel" [(ngModel)]="clientPhone" maxlength="20" inputmode="tel" />
            </div>
            <div class="field field--wide">
              <label for="obs">Observações</label>
              <textarea
                id="obs"
                name="obs"
                [(ngModel)]="notes"
                maxlength="2000"
                rows="3"
              ></textarea>
            </div>
          </div>
        </fieldset>

        <fieldset class="card">
          <legend>{{ typeLabel(type()) }}: dados do serviço</legend>
          <div class="form-grid">
            @for (f of fields(); track f.key) {
              <div class="field" [class.field--wide]="f.kind === 'textarea'">
                <label [for]="'d-' + f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
                @switch (f.kind) {
                  @case ('textarea') {
                    <textarea
                      [id]="'d-' + f.key"
                      [name]="f.key"
                      rows="2"
                      [attr.maxlength]="f.max"
                      [ngModel]="value(f)"
                      (ngModelChange)="set(f, $event)"
                    ></textarea>
                  }
                  @case ('number') {
                    <input
                      [id]="'d-' + f.key"
                      [name]="f.key"
                      type="number"
                      min="0"
                      max="20"
                      [ngModel]="value(f)"
                      (ngModelChange)="set(f, $event)"
                    />
                  }
                  @default {
                    <input
                      [id]="'d-' + f.key"
                      [name]="f.key"
                      [type]="f.kind"
                      [attr.maxlength]="f.max"
                      [ngModel]="value(f)"
                      (ngModelChange)="set(f, $event)"
                    />
                  }
                }
              </div>
            }
            <div class="field field--wide">
              <label for="pax">Outros passageiros (um por linha, até 10)</label>
              <textarea id="pax" name="pax" rows="3" [(ngModel)]="passengers"></textarea>
            </div>
          </div>
        </fieldset>

        <div class="actions">
          <button type="submit" class="btn btn--primary" [disabled]="saving()">
            {{ saving() ? 'Salvando…' : id() ? 'Salvar correção' : 'Emitir voucher' }}
          </button>
        </div>
      </form>
    </section>
  `,
  styles: `
    fieldset {
      margin: 0 0 var(--space-3);
    }
    legend {
      font-weight: 700;
    }
    .choice {
      display: inline-flex;
      gap: var(--space-1);
      align-items: center;
      min-height: 2.75rem;
      margin-inline-end: var(--space-3);
    }
  `,
})
export class VoucherFormPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly session = inject(CrmSession);

  readonly id = input<string>('');
  protected readonly types = Object.keys(VOUCHER_TYPE) as VoucherType[];
  protected readonly type = signal<VoucherType>('FLIGHT');
  protected readonly fields = computed(() => VOUCHER_FIELDS[this.type()]);
  protected readonly details = signal<VoucherDetails>({});
  protected readonly source = signal<CrmFile | null>(null);
  protected readonly cpfMasked = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly extracting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aiConfigured = computed(
    () => this.session.integrations().find((i) => i.key === 'AI_TEXT')?.configured ?? false,
  );

  protected clientName = '';
  protected clientCpf = '';
  protected clientPhone = '';
  protected notes = '';
  protected passengers = '';
  private version: number | null = null;

  constructor() {
    void this.session.ready();
    effect(() => {
      const id = this.id();
      if (id) void this.load(id);
    });
  }

  protected typeLabel(type: VoucherType): string {
    return VOUCHER_TYPE[type];
  }

  protected value(field: DetailField): unknown {
    return this.details()[field.key] ?? '';
  }

  protected set(field: DetailField, value: unknown): void {
    const normalized =
      field.kind === 'number'
        ? value === '' || value === null
          ? null
          : Number(value)
        : `${value ?? ''}`.trim() === ''
          ? null
          : value;
    this.details.update((d) => ({ ...d, [field.key]: normalized }));
  }

  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.error.set(null);
    try {
      this.source.set(await firstValueFrom(this.api.uploadFile('VOUCHER_SOURCE', file)));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.uploading.set(false);
      input.value = '';
    }
  }

  protected async extract(): Promise<void> {
    const source = this.source();
    if (!source) return;
    this.extracting.set(true);
    this.error.set(null);
    try {
      const found = await firstValueFrom(this.api.extractVoucher(this.type(), source.id));
      this.details.update((d) => ({ ...d, ...found }));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.extracting.set(false);
    }
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    const missing = missingFields(this.type(), this.details());
    if (!this.clientName.trim()) missing.unshift('Nome');
    if (missing.length) {
      this.error.set(`Preencha: ${missing.join(', ')}.`);
      return;
    }
    this.saving.set(true);
    try {
      const body = this.request();
      const saved = this.id()
        ? await firstValueFrom(this.api.updateVoucher(this.id(), body))
        : await firstValueFrom(this.api.issueVoucher(body));
      await this.router.navigate(['/vouchers', saved.id]);
    } catch (error) {
      this.error.set(errorMessage(error));
      if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') this.version = null;
    } finally {
      this.saving.set(false);
    }
  }

  /** Só os campos do tipo escolhido vão para a API; CPF em branco = null (na correção, mantém o guardado). */
  request(): VoucherRequest {
    const allowed = new Set(this.fields().map((f) => f.key));
    const details: VoucherDetails = {};
    for (const [key, value] of Object.entries(this.details())) {
      if (allowed.has(key as DetailField['key'])) (details as Record<string, unknown>)[key] = value;
    }
    const names = this.passengers
      .split('\n')
      .map((p) => p.trim())
      .filter(Boolean)
      .slice(0, 10);
    details.passengers = names.length ? names : null;
    return {
      type: this.type(),
      clientName: this.clientName.trim(),
      clientCpf: this.clientCpf.trim() || null,
      clientPhone: this.clientPhone.trim() || null,
      notes: this.notes.trim() || null,
      details,
      sourceFileId: this.source()?.id ?? null,
      version: this.version,
    };
  }

  private async load(id: string): Promise<void> {
    try {
      const v = await firstValueFrom(this.api.voucher(id));
      this.version = v.version;
      this.type.set(v.type);
      this.details.set({ ...v.details });
      this.source.set(v.sourceFile ?? null);
      this.cpfMasked.set(v.clientCpfMasked ?? null);
      this.clientName = v.clientName;
      this.clientPhone = v.clientPhone ?? '';
      this.notes = v.notes ?? '';
      this.passengers = (v.details.passengers ?? []).join('\n');
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
}
