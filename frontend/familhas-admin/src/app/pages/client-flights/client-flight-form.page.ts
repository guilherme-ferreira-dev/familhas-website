import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  ClientFlight,
  ClientFlightRequest,
  ClientFlightTravelerRequest,
} from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { errorMessage } from '../../core/errors/api-error';
import { fromDateTimeInput, toDateTimeInput } from '../../shared/crm-format';

/**
 * Cadastro e edição de voo. CPF: a tela recebe só o mascarado; o campo começa vazio e só vai para a API quando
 * alguém digita (vazio + passageiro existente = mantém o CPF guardado).
 */
@Component({
  selector: 'app-client-flight-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a [routerLink]="id() ? ['/voos-clientes', id()] : '/voos-clientes'">← Voltar</a></p>
      <div class="page-header">
        <h1 id="titulo">{{ id() ? 'Editar voo' : 'Novo voo' }}</h1>
      </div>

      @if (error(); as message) {
        <p class="alert" role="alert">{{ message }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <fieldset class="card">
          <legend>Voo</legend>
          <div class="form-grid">
            <div class="field">
              <label for="cia">Companhia *</label>
              <input id="cia" formControlName="airline" maxlength="60" required />
            </div>
            <div class="field">
              <label for="loc">Localizador</label>
              <input id="loc" formControlName="locator" maxlength="20" class="mono" />
            </div>
            <div class="field">
              <label for="orig">Origem *</label>
              <input
                id="orig"
                formControlName="origin"
                maxlength="60"
                placeholder="Brasília (BSB)"
              />
            </div>
            <div class="field">
              <label for="dest">Destino *</label>
              <input
                id="dest"
                formControlName="destination"
                maxlength="60"
                placeholder="Maceió (MCZ)"
              />
            </div>
            <div class="field">
              <label for="ida">Ida (horário local) *</label>
              <input id="ida" type="datetime-local" formControlName="outboundAt" />
            </div>
            <div class="field">
              <label for="voo-ida">Voo de ida</label>
              <input id="voo-ida" formControlName="outboundFlightNumber" maxlength="20" />
            </div>
            <div class="field">
              <label for="volta">Volta (horário local)</label>
              <input id="volta" type="datetime-local" formControlName="returnAt" />
            </div>
            <div class="field">
              <label for="voo-volta">Voo de volta</label>
              <input id="voo-volta" formControlName="returnFlightNumber" maxlength="20" />
            </div>
            <div class="field field--wide">
              <label for="obs">Observações</label>
              <textarea id="obs" formControlName="notes" maxlength="2000" rows="3"></textarea>
            </div>
          </div>
        </fieldset>

        <fieldset class="card" formArrayName="travelers">
          <legend>Passageiros *</legend>
          @for (t of travelers.controls; track t; let i = $index) {
            <div class="form-grid traveler" [formGroupName]="i">
              <div class="field">
                <label [for]="'nome-' + i">Nome completo *</label>
                <input [id]="'nome-' + i" formControlName="fullName" maxlength="150" />
              </div>
              <div class="field">
                <label [for]="'cpf-' + i">CPF</label>
                <input
                  [id]="'cpf-' + i"
                  formControlName="cpf"
                  maxlength="14"
                  inputmode="numeric"
                  autocomplete="off"
                  [placeholder]="t.value.cpfMasked ?? '000.000.000-00'"
                  [attr.aria-describedby]="t.value.cpfMasked ? 'cpf-dica-' + i : null"
                />
                @if (t.value.cpfMasked) {
                  <small [id]="'cpf-dica-' + i" class="muted"
                    >Guardado: {{ t.value.cpfMasked }}. Deixe em branco para manter.</small
                  >
                }
              </div>
              <div class="field">
                <label [for]="'tel-' + i">Telefone</label>
                <input [id]="'tel-' + i" formControlName="phone" maxlength="20" inputmode="tel" />
              </div>
              @if (travelers.length > 1) {
                <button type="button" class="btn btn--small" (click)="travelers.removeAt(i)">
                  Remover passageiro
                </button>
              }
            </div>
          }
          @if (travelers.length < 20) {
            <button type="button" class="btn btn--small" (click)="addTraveler()">
              Adicionar passageiro
            </button>
          }
        </fieldset>

        <div class="actions">
          <button type="submit" class="btn btn--primary" [disabled]="saving()">
            {{ saving() ? 'Salvando…' : 'Salvar' }}
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
    .traveler {
      padding-bottom: var(--space-3);
      margin-bottom: var(--space-3);
      border-bottom: 1px solid var(--color-border);
      align-items: end;
    }
  `,
})
export class ClientFlightFormPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** Rota /voos-clientes/:id/editar; vazio = novo. */
  readonly id = input<string>('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private version: number | null = null;

  protected readonly form = this.fb.group({
    airline: ['', [Validators.required, Validators.maxLength(60)]],
    locator: [''],
    origin: ['', Validators.required],
    destination: ['', Validators.required],
    outboundAt: ['', Validators.required],
    outboundFlightNumber: [''],
    returnAt: [''],
    returnFlightNumber: [''],
    notes: [''],
    travelers: this.fb.array([this.traveler()]),
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) void this.load(id);
    });
  }

  protected get travelers(): FormArray {
    return this.form.controls.travelers;
  }

  protected addTraveler(): void {
    this.travelers.push(this.traveler());
  }

  protected async submit(): Promise<void> {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Preencha companhia, origem, destino, ida e o nome de cada passageiro.');
      return;
    }
    this.saving.set(true);
    try {
      const body = this.request();
      const saved = this.id()
        ? await firstValueFrom(this.api.updateClientFlight(this.id(), body))
        : await firstValueFrom(this.api.createClientFlight(body));
      await this.router.navigate(['/voos-clientes', saved.id]);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  /** Corpo para a API. CPF em branco num passageiro existente vai como null (mantém o guardado). */
  request(): ClientFlightRequest {
    const v = this.form.getRawValue();
    return {
      airline: v.airline.trim(),
      locator: v.locator.trim() || null,
      origin: v.origin.trim(),
      destination: v.destination.trim(),
      outboundAt: fromDateTimeInput(v.outboundAt)!,
      outboundFlightNumber: v.outboundFlightNumber.trim() || null,
      returnAt: fromDateTimeInput(v.returnAt),
      returnFlightNumber: v.returnFlightNumber.trim() || null,
      notes: v.notes.trim() || null,
      travelers: v.travelers.map(
        (t: {
          id: string | null;
          fullName: string;
          cpf: string;
          phone: string;
        }): ClientFlightTravelerRequest => ({
          id: t.id,
          fullName: t.fullName.trim(),
          cpf: t.cpf.trim() || null,
          phone: t.phone.trim() || null,
        }),
      ),
      version: this.version,
    };
  }

  private traveler(value?: ClientFlight['travelers'][number]) {
    return this.fb.group({
      id: this.fb.control<string | null>(value?.id ?? null),
      fullName: [value?.fullName ?? '', [Validators.required, Validators.maxLength(150)]],
      cpf: [''],
      cpfMasked: this.fb.control<string | null>(value?.cpfMasked ?? null),
      phone: [value?.phone ?? ''],
    });
  }

  private async load(id: string): Promise<void> {
    try {
      const f = await firstValueFrom(this.api.clientFlight(id));
      this.version = f.version;
      this.form.patchValue({
        airline: f.airline,
        locator: f.locator ?? '',
        origin: f.origin,
        destination: f.destination,
        outboundAt: toDateTimeInput(f.outboundAt),
        outboundFlightNumber: f.outboundFlightNumber ?? '',
        returnAt: toDateTimeInput(f.returnAt ?? null),
        returnFlightNumber: f.returnFlightNumber ?? '',
        notes: f.notes ?? '',
      });
      this.travelers.clear();
      f.travelers.forEach((t) => this.travelers.push(this.traveler(t)));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
}
