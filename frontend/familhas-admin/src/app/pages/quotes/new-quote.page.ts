import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map, of } from 'rxjs';
import type { Client, ManualQuoteRequest } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { CatalogApi, curatedOfferId } from '../../core/admin/catalog-api';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { formatDate, money } from '../../shared/format';
import { BOARD_TYPE, CAR_CATEGORY } from '../../shared/labels';

type Mode = 'package' | 'items';

/**
 * Cotação manual para um cliente (ADR 0011). Os preços vêm do catálogo, calculados pela API como na Loja;
 * o admin só escolhe os itens e, se quiser, um desconto em valor (limitado à margem).
 */
@Component({
  selector: 'app-new-quote-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/cotacoes">← Cotações</a></p>
      <div class="page-header">
        <div>
          <h1 id="titulo">Nova cotação manual</h1>
          <p>O cliente vê a cotação na Loja, em "Minhas cotações", e paga pelo fluxo normal.</p>
        </div>
      </div>

      <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
        <fieldset class="card stack">
          <legend><h2>Cliente</h2></legend>
          @if (client(); as c) {
            <p>
              <strong>{{ c.name }}</strong> · {{ c.email }}
              @if (c.status === 'INACTIVE') {
                <span class="field-error">(inativo: reative antes de cotar)</span>
              }
              <button type="button" class="btn btn--small" (click)="client.set(null)">
                Trocar
              </button>
            </p>
          } @else {
            <div class="filters">
              <div class="field field--wide">
                <label for="busca-cliente">Buscar por nome ou e-mail</label>
                <input
                  id="busca-cliente"
                  #busca
                  type="search"
                  maxlength="100"
                  (keydown.enter)="$event.preventDefault(); searchClients(busca.value)"
                />
              </div>
              <button type="button" class="btn" (click)="searchClients(busca.value)">Buscar</button>
            </div>
            @if (clientResults().length > 0) {
              <ul class="results" aria-label="Clientes encontrados">
                @for (c of clientResults(); track c.id) {
                  <li>
                    <button type="button" class="btn btn--small" (click)="pick(c)">Escolher</button>
                    {{ c.name }} · {{ c.email }}
                  </li>
                }
              </ul>
            } @else if (searched()) {
              <p class="muted">Nenhum cliente encontrado.</p>
            }
          }
        </fieldset>

        <fieldset class="card stack">
          <legend><h2>Viagem</h2></legend>
          <div class="form-grid">
            <div class="field">
              <label for="passageiros">Passageiros</label>
              <input id="passageiros" type="number" min="1" max="9" formControlName="passengers" />
            </div>
            <div class="field">
              <span class="label" id="modo">Montar com</span>
              <div class="checks" role="radiogroup" aria-labelledby="modo">
                <label><input type="radio" formControlName="mode" value="package" /> Pacote</label>
                <label
                  ><input type="radio" formControlName="mode" value="items" /> Itens avulsos</label
                >
              </div>
            </div>
          </div>

          @if (mode() === 'package') {
            <div class="field">
              <label for="pacote">Pacote</label>
              <select id="pacote" formControlName="travelPackageId">
                <option value="">Escolha um pacote</option>
                @for (p of packages(); track p.id) {
                  <option [value]="p.id">
                    {{ p.title }} · {{ money(p.pricePromotion ?? p.price, p.currency) }}
                  </option>
                }
              </select>
            </div>
          } @else {
            <div class="form-grid">
              <div class="field field--wide">
                <label for="voo">Voo</label>
                <select id="voo" formControlName="flightId">
                  <option value="">Sem voo</option>
                  @for (f of flights(); track f.id) {
                    <option [value]="f.id">
                      {{ f.companyName }} · {{ f.boardingAirport }} → {{ f.returnAirport }} ·
                      {{ localDate(f.boardingDate) }} ·
                      {{ money(f.pricePerPassenger, f.currency) }}/pax
                    </option>
                  }
                </select>
              </div>
              <div class="field field--wide">
                <label for="quarto">Hospedagem</label>
                <select id="quarto" formControlName="roomOfferId">
                  <option value="">Sem hospedagem</option>
                  @for (r of rooms(); track r.id) {
                    <option [value]="r.id">
                      {{ hotelName(r.hotelId) }} · {{ r.roomType }} · {{ board(r.boardType) }} ·
                      {{ money(r.pricePerNight, r.currency) }}/noite
                    </option>
                  }
                </select>
              </div>
              @if (form.controls.roomOfferId.value) {
                <div class="field">
                  <label for="checkin">Check-in</label>
                  <input id="checkin" type="date" formControlName="checkIn" />
                </div>
                <div class="field">
                  <label for="checkout">Check-out</label>
                  <input id="checkout" type="date" formControlName="checkOut" />
                </div>
              }
              <div class="field field--wide">
                <label for="carro">Carro</label>
                <select id="carro" formControlName="carRentalOfferId">
                  <option value="">Sem carro</option>
                  @for (c of cars(); track c.id) {
                    <option [value]="c.id">
                      {{ c.companyName }} · {{ c.carModel }} · {{ carCategory(c.carCategory) }} ·
                      {{ money(c.pricePerDay, c.currency) }}/dia
                    </option>
                  }
                </select>
              </div>
              @if (form.controls.carRentalOfferId.value) {
                <div class="field">
                  <label for="retirada">Retirada</label>
                  <input id="retirada" type="date" formControlName="pickUpDate" />
                </div>
                <div class="field">
                  <label for="devolucao">Devolução</label>
                  <input id="devolucao" type="date" formControlName="dropOffDate" />
                </div>
              }
            </div>
          }
        </fieldset>

        <fieldset class="card stack">
          <legend><h2>Condições</h2></legend>
          <div class="form-grid">
            <div class="field">
              <label for="desconto">Desconto manual (R$)</label>
              <input
                id="desconto"
                type="number"
                min="0"
                step="0.01"
                formControlName="manualDiscount"
                aria-describedby="desconto-ajuda"
              />
              <small id="desconto-ajuda" class="muted"
                >Até a margem que sobra depois da promoção. O total nunca fica abaixo do
                custo.</small
              >
            </div>
            <div class="field">
              <label for="validade">Validade (horas)</label>
              <input
                id="validade"
                type="number"
                min="1"
                max="720"
                formControlName="validityHours"
              />
            </div>
          </div>
          <div class="field">
            <label for="nota">Nota interna (o cliente não vê)</label>
            <textarea id="nota" maxlength="500" formControlName="internalNote"></textarea>
          </div>
        </fieldset>

        @if (problem(); as p) {
          <p class="alert" role="alert">{{ p }}</p>
        }
        <div class="actions">
          <button type="submit" class="btn btn--primary" [disabled]="saving()">
            {{ saving() ? 'Calculando…' : 'Criar cotação' }}
          </button>
          <a class="btn" routerLink="/cotacoes">Cancelar</a>
        </div>
      </form>
    </section>
  `,
  styles: `
    .stack {
      display: grid;
      gap: var(--space-3);
    }
    legend {
      padding: 0;
      margin-bottom: var(--space-2);
    }
    .results {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: var(--space-2);
    }
    p {
      margin: 0;
    }
  `,
})
export class NewQuotePage {
  private readonly api = inject(AdminApi);
  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);

  /** ?cliente= vindo do detalhe do cliente. */
  readonly cliente = input<string>();

  protected readonly client = signal<Client | null>(null);
  protected readonly clientResults = signal<Client[]>([]);
  protected readonly searched = signal(false);
  protected readonly saving = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly money = money;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    passengers: [2, [Validators.required, Validators.min(1), Validators.max(9)]],
    mode: ['package' as Mode],
    travelPackageId: [''],
    flightId: [''],
    roomOfferId: [''],
    checkIn: [''],
    checkOut: [''],
    carRentalOfferId: [''],
    pickUpDate: [''],
    dropOffDate: [''],
    manualDiscount: [0, [Validators.min(0)]],
    validityHours: [24, [Validators.min(1), Validators.max(720)]],
    internalNote: ['', Validators.maxLength(500)],
  });

  protected readonly mode = toSignal(this.form.controls.mode.valueChanges, {
    initialValue: 'package' as Mode,
  });

  private readonly active = { status: 'ACTIVE', size: 100 };
  private readonly packagesRes = rxResource({
    stream: () =>
      this.catalog
        .list('travel-packages', { ...this.active, sort: 'title,asc' })
        .pipe(map((p) => p.content)),
  });
  private readonly flightsRes = rxResource({
    params: () => this.mode(),
    stream: ({ params }) =>
      params === 'items'
        ? this.catalog
            .list('flights', { ...this.active, sort: 'boardingDate,asc' })
            .pipe(map((p) => p.content))
        : of([]),
  });
  private readonly roomsRes = rxResource({
    params: () => this.mode(),
    stream: ({ params }) =>
      params === 'items'
        ? this.catalog.list('room-offers', this.active).pipe(map((p) => p.content))
        : of([]),
  });
  private readonly hotelsRes = rxResource({
    params: () => this.mode(),
    stream: ({ params }) =>
      params === 'items'
        ? this.catalog.list('hotels', { size: 100 }).pipe(map((p) => p.content))
        : of([]),
  });
  private readonly carsRes = rxResource({
    params: () => this.mode(),
    stream: ({ params }) =>
      params === 'items'
        ? this.catalog.list('car-rental-offers', this.active).pipe(map((p) => p.content))
        : of([]),
  });
  protected readonly packages = computed(() => this.packagesRes.value() ?? []);
  protected readonly flights = computed(() => this.flightsRes.value() ?? []);
  protected readonly rooms = computed(() => this.roomsRes.value() ?? []);
  protected readonly cars = computed(() => this.carsRes.value() ?? []);
  private readonly hotelNames = computed(
    () => new Map((this.hotelsRes.value() ?? []).map((h) => [h.id, h.name])),
  );

  constructor() {
    effect(() => {
      const id = this.cliente();
      if (id) {
        this.api
          .client(id)
          .subscribe({ next: (c) => this.client.set(c), error: () => this.client.set(null) });
      }
    });
  }

  protected hotelName(id: string): string {
    return this.hotelNames().get(id) ?? 'Hotel';
  }

  protected board(value: keyof typeof BOARD_TYPE): string {
    return BOARD_TYPE[value];
  }

  protected carCategory(value: keyof typeof CAR_CATEGORY): string {
    return CAR_CATEGORY[value];
  }

  /** Data do voo: horário local do voo, sem fuso (LocalDateTime da API). */
  protected localDate(value: string): string {
    return formatDate(value.slice(0, 10));
  }

  protected searchClients(q: string): void {
    this.api.clients({ q: q.trim(), size: 10, sort: 'name,asc' }).subscribe((page) => {
      this.clientResults.set(page.content);
      this.searched.set(true);
    });
  }

  protected pick(c: Client): void {
    this.client.set(c);
    this.clientResults.set([]);
    this.searched.set(false);
  }

  /** Monta o pedido; devolve a mensagem de erro de preenchimento, se houver. */
  buildRequest(): ManualQuoteRequest | string {
    const c = this.client();
    if (!c) return 'Escolha o cliente.';
    if (this.form.invalid) return 'Revise passageiros, desconto e validade.';
    const v = this.form.getRawValue();
    const base = {
      clientId: c.id,
      passengers: v.passengers,
      manualDiscount: v.manualDiscount > 0 ? Math.round(v.manualDiscount * 100) / 100 : null,
      validityHours: v.validityHours,
      internalNote: v.internalNote.trim() || null,
    };
    if (v.mode === 'package') {
      if (!v.travelPackageId) return 'Escolha o pacote.';
      return { ...base, travelPackageId: v.travelPackageId };
    }
    if (!v.flightId && !v.roomOfferId && !v.carRentalOfferId) return 'Escolha ao menos um item.';
    if (v.roomOfferId && !(v.checkIn && v.checkOut && v.checkOut > v.checkIn)) {
      return 'Informe check-in e check-out (saída depois da entrada).';
    }
    if (v.carRentalOfferId && !(v.pickUpDate && v.dropOffDate && v.dropOffDate >= v.pickUpDate)) {
      return 'Informe retirada e devolução do carro.';
    }
    return {
      ...base,
      flight: v.flightId ? { offerId: curatedOfferId(v.flightId) } : null,
      hotel: v.roomOfferId
        ? { offerId: curatedOfferId(v.roomOfferId), checkIn: v.checkIn, checkOut: v.checkOut }
        : null,
      car: v.carRentalOfferId
        ? {
            offerId: curatedOfferId(v.carRentalOfferId),
            pickUpDate: v.pickUpDate,
            dropOffDate: v.dropOffDate,
          }
        : null,
    };
  }

  protected submit(): void {
    const request = this.buildRequest();
    if (typeof request === 'string') {
      this.problem.set(request);
      return;
    }
    this.problem.set(null);
    this.saving.set(true);
    this.api.createQuote(request).subscribe({
      next: (quote) => void this.router.navigate(['/cotacoes', quote.id]),
      error: (error: unknown) => {
        this.saving.set(false);
        this.problem.set(describe(error));
      },
    });
  }
}

function describe(error: unknown): string {
  if (error instanceof ApiError && error.code === 'QUOTE_BELOW_COST') {
    const max = Number(error.extra['maxManualDiscount']);
    return `${errorMessage(error)} Desconto máximo para esta cotação: ${money(Number.isFinite(max) ? max : 0)}.`;
  }
  if (error instanceof ApiError && error.kind === 'unprocessable' && error.detail) {
    // Ex.: oferta indisponível, assentos insuficientes: o detalhe da API ajuda a equipe a resolver.
    return `${errorMessage(error)} (${error.detail})`;
  }
  return errorMessage(error);
}
