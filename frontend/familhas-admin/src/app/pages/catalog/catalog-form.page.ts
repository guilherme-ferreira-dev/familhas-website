import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, map, of } from 'rxjs';
import type { CarRentalOffer, RoomOffer } from '../../core/api/api-types';
import { CatalogApi } from '../../core/admin/catalog-api';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { money } from '../../shared/format';
import { BOARD_TYPE } from '../../shared/labels';
import {
  COMPONENT_TYPES,
  ComponentRow,
  DEFINITIONS,
  FieldDef,
  Option,
} from './catalog-definitions';

/** Cadastro e edição de um item do catálogo, a partir da definição de campos. */
@Component({
  selector: 'app-catalog-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    @let d = def();
    <p>
      <a [routerLink]="['/catalogo', tipo()]">← {{ d.title }}</a>
    </p>
    <h2>{{ id() ? 'Editar' : 'Novo' }} {{ d.singular }}</h2>
    @if (existing.error()) {
      <p class="alert" role="alert">{{ message(existing.error()) }}</p>
    }
    <form class="card stack" [formGroup]="form()" (ngSubmit)="save()" novalidate>
      <div class="form-grid">
        @for (f of simpleFields(); track f.key) {
          <div class="field" [class.full]="f.type === 'textarea'">
            <label [for]="f.key">{{ f.label }}{{ f.required ? ' *' : '' }}</label>
            @switch (f.type) {
              @case ('select') {
                <select
                  [id]="f.key"
                  [formControlName]="f.key"
                  [attr.aria-invalid]="invalid(f.key)"
                  [attr.aria-describedby]="f.key + '-erro'"
                >
                  <option value="">Escolha</option>
                  @for (o of optionsFor(f); track o.value) {
                    <option [value]="o.value">{{ o.label }}</option>
                  }
                </select>
              }
              @case ('textarea') {
                <textarea
                  [id]="f.key"
                  [formControlName]="f.key"
                  [attr.maxlength]="f.maxLength"
                ></textarea>
              }
              @case ('datetime') {
                <input
                  [id]="f.key"
                  type="datetime-local"
                  [formControlName]="f.key"
                  [attr.aria-invalid]="invalid(f.key)"
                />
              }
              @case ('number') {
                <input
                  [id]="f.key"
                  type="number"
                  [formControlName]="f.key"
                  [attr.min]="f.min"
                  [attr.max]="f.max"
                  [attr.step]="f.step"
                  [attr.aria-invalid]="invalid(f.key)"
                  [attr.aria-describedby]="f.hint ? f.key + '-ajuda' : null"
                />
              }
              @default {
                <input
                  [id]="f.key"
                  type="text"
                  [formControlName]="f.key"
                  [attr.maxlength]="f.maxLength"
                  [attr.aria-invalid]="invalid(f.key)"
                  [attr.aria-describedby]="f.hint ? f.key + '-ajuda' : null"
                />
              }
            }
            @if (f.hint) {
              <small class="muted" [id]="f.key + '-ajuda'">{{ f.hint }}</small>
            }
            @if (fieldError(f.key); as e) {
              <p class="field-error" [id]="f.key + '-erro'">{{ e }}</p>
            }
          </div>
        }
      </div>

      @if (hasComponents()) {
        <fieldset class="stack" formArrayName="components">
          <legend>Hospedagem e carro do pacote</legend>
          <small class="muted"
            >Noites de hotel ou dias de carro a partir do embarque. O voo vem do campo acima.</small
          >
          @for (row of componentRows().controls; track $index; let i = $index) {
            <div class="component-row" [formGroupName]="i">
              <div class="field">
                <label [for]="'tipo-' + i">Tipo</label>
                <select [id]="'tipo-' + i" formControlName="type">
                  @for (t of componentTypes; track t.value) {
                    <option [value]="t.value">{{ t.label }}</option>
                  }
                </select>
              </div>
              <div class="field field--wide">
                <label [for]="'oferta-' + i">{{
                  row.value.type === 'HOTEL_STAY' ? 'Quarto' : 'Carro'
                }}</label>
                <select [id]="'oferta-' + i" formControlName="offerId">
                  <option value="">Escolha</option>
                  @for (
                    o of row.value.type === 'HOTEL_STAY' ? roomOptions() : carOptions();
                    track o.value
                  ) {
                    <option [value]="o.value">{{ o.label }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label [for]="'qtd-' + i">{{
                  row.value.type === 'HOTEL_STAY' ? 'Noites' : 'Dias'
                }}</label>
                <input
                  [id]="'qtd-' + i"
                  type="number"
                  min="1"
                  max="60"
                  formControlName="quantity"
                />
              </div>
              <button type="button" class="btn btn--small btn--danger" (click)="removeComponent(i)">
                Remover
              </button>
            </div>
          }
          <div>
            <button type="button" class="btn btn--small" (click)="addComponent()">
              Adicionar hospedagem ou carro
            </button>
          </div>
        </fieldset>
      }

      @if (problem(); as p) {
        <p class="alert" role="alert">{{ p }}</p>
      }
      <div class="actions">
        <button type="submit" class="btn btn--primary" [disabled]="saving()">
          {{ saving() ? 'Salvando…' : 'Salvar' }}
        </button>
        <a class="btn" [routerLink]="['/catalogo', tipo()]">Cancelar</a>
      </div>
    </form>
  `,
  styles: `
    :host,
    .stack {
      display: grid;
      gap: var(--space-3);
    }
    .full {
      grid-column: 1 / -1;
    }
    .component-row {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: end;
      padding-bottom: var(--space-2);
      border-bottom: 1px solid var(--color-border);
    }
  `,
})
export class CatalogFormPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly tipo = input.required<string>();
  readonly id = input<string>();

  protected readonly def = computed(() => DEFINITIONS[this.tipo()] ?? DEFINITIONS['voos']);
  protected readonly simpleFields = computed(() =>
    this.def().fields.filter((f) => f.type !== 'components'),
  );
  protected readonly hasComponents = computed(() =>
    this.def().fields.some((f) => f.type === 'components'),
  );
  protected readonly componentTypes = COMPONENT_TYPES;
  protected readonly form = computed(() => buildForm(this.def().fields, this.def().defaults));
  protected readonly saving = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly serverErrors = signal<Readonly<Record<string, string>>>({});

  protected readonly existing = rxResource({
    params: () => ({ def: this.def(), id: this.id() }),
    stream: ({ params }) => (params.id ? this.api.get(params.def.resource, params.id) : of(null)),
  });

  /** Opções de cada select ligado a outro recurso. Campos com a mesma fonte (ex.: origem e destino) dividem a chamada. */
  private readonly optionLists = rxResource({
    params: () => this.def(),
    stream: ({ params }) => {
      const sources = params.fields.filter((f) => f.optionsFrom);
      if (sources.length === 0) return of({} as Record<string, Option[]>);
      const keyOf = (f: FieldDef) =>
        JSON.stringify([f.optionsFrom!.resource, f.optionsFrom!.params ?? {}]);
      const unique = new Map(sources.map((f) => [keyOf(f), f.optionsFrom!]));
      return forkJoin(
        Object.fromEntries(
          [...unique].map(([key, source]) => [
            key,
            this.api
              .list(source.resource, source.params ?? { size: 100 })
              .pipe(map((page) => page.content)),
          ]),
        ),
      ).pipe(
        map((lists) =>
          Object.fromEntries(
            sources.map((f) => [
              f.key,
              lists[keyOf(f)].map((item) => ({
                value: item.id,
                label: f.optionsFrom!.label(item as never),
              })),
            ]),
          ),
        ),
      );
    },
  });

  private readonly packageOffers = rxResource({
    params: () => this.hasComponents(),
    stream: ({ params }) =>
      params
        ? forkJoin({
            rooms: this.api.list('room-offers', { size: 100 }).pipe(map((p) => p.content)),
            hotels: this.api.list('hotels', { size: 100 }).pipe(map((p) => p.content)),
            cars: this.api.list('car-rental-offers', { size: 100 }).pipe(map((p) => p.content)),
          })
        : of(null),
  });

  protected readonly roomOptions = computed<Option[]>(() => {
    const offers = this.packageOffers.value();
    if (!offers) return [];
    const hotels = new Map(offers.hotels.map((h) => [h.id, h.name]));
    return offers.rooms.map((r: RoomOffer) => ({
      value: r.id,
      label: `${hotels.get(r.hotelId) ?? 'Hotel'} · ${r.roomType} · ${BOARD_TYPE[r.boardType]} · ${money(r.pricePerNight, r.currency)}/noite`,
    }));
  });

  protected readonly carOptions = computed<Option[]>(() =>
    (this.packageOffers.value()?.cars ?? []).map((c: CarRentalOffer) => ({
      value: c.id,
      label: `${c.companyName} · ${c.carModel} · ${money(c.pricePerDay, c.currency)}/dia`,
    })),
  );

  constructor() {
    effect(() => {
      const item = this.existing.value();
      const form = this.form();
      if (!item) return;
      const value = this.def().toForm(item);
      const rows = (value['components'] as ComponentRow[] | undefined) ?? [];
      const array = form.get('components') as FormArray | null;
      array?.clear();
      rows.forEach((row) => array?.push(componentGroup(row)));
      form.patchValue(value);
    });
  }

  protected componentRows(): FormArray<FormGroup> {
    return this.form().get('components') as FormArray<FormGroup>;
  }

  protected addComponent(): void {
    this.componentRows().push(componentGroup({ type: 'HOTEL_STAY', offerId: '', quantity: 1 }));
  }

  protected removeComponent(index: number): void {
    this.componentRows().removeAt(index);
  }

  protected optionsFor(f: FieldDef): readonly Option[] {
    return f.options ?? this.optionLists.value()?.[f.key] ?? [];
  }

  protected invalid(key: string): boolean {
    const control = this.form().get(key);
    return Boolean((control?.invalid && control.touched) || this.serverErrors()[key]);
  }

  protected fieldError(key: string): string | null {
    const control = this.form().get(key);
    if (control?.touched && control.errors) {
      if (control.errors['required']) return 'Obrigatório.';
      if (control.errors['min']) return `Mínimo ${control.errors['min'].min}.`;
      if (control.errors['max']) return `Máximo ${control.errors['max'].max}.`;
      if (control.errors['maxlength']) return 'Texto longo demais.';
    }
    return this.serverErrors()[key] ? 'Valor recusado pela API. Revise.' : null;
  }

  protected message(error: unknown): string {
    return errorMessage(error);
  }

  protected save(): void {
    const form = this.form();
    form.markAllAsTouched();
    if (form.invalid) {
      this.problem.set('Revise os campos marcados.');
      return;
    }
    const d = this.def();
    const request = d.toRequest(form.getRawValue()) as never;
    const id = this.id();
    this.saving.set(true);
    this.problem.set(null);
    this.serverErrors.set({});
    (id
      ? this.api.update(d.resource, id, request)
      : this.api.create(d.resource, request)
    ).subscribe({
      next: () => {
        this.notifications.info(
          `${d.singular.charAt(0).toUpperCase()}${d.singular.slice(1)} salvo.`,
        );
        void this.router.navigate(['/catalogo', this.tipo()]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError) this.serverErrors.set(error.fieldErrors);
        const detail =
          error instanceof ApiError && error.kind === 'validation' ? error.detail : null;
        this.problem.set(detail ? `${errorMessage(error)} (${detail})` : errorMessage(error));
      },
    });
  }
}

function buildForm(
  fields: readonly FieldDef[],
  defaults: Readonly<Record<string, unknown>>,
): FormGroup {
  const controls: Record<string, AbstractControl> = {};
  for (const f of fields) {
    if (f.type === 'components') {
      controls[f.key] = new FormArray<FormGroup>([]);
      continue;
    }
    const validators: ValidatorFn[] = [];
    if (f.required) validators.push(Validators.required);
    if (f.min !== undefined) validators.push(Validators.min(f.min));
    if (f.max !== undefined) validators.push(Validators.max(f.max));
    if (f.maxLength !== undefined) validators.push(Validators.maxLength(f.maxLength));
    controls[f.key] = new FormControl(defaults[f.key] ?? '', validators);
  }
  return new FormGroup(controls);
}

function componentGroup(row: ComponentRow): FormGroup {
  return new FormGroup({
    type: new FormControl(row.type, { nonNullable: true }),
    offerId: new FormControl(row.offerId, { nonNullable: true, validators: Validators.required }),
    quantity: new FormControl(row.quantity, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1), Validators.max(60)],
    }),
  });
}
