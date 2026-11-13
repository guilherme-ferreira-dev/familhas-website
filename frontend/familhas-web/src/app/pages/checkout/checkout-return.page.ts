import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import type { Booking } from '../../core/api/api-types';
import { CheckoutApi } from '../../core/checkout/checkout-api';
import { pollBooking } from '../../core/checkout/booking-polling';
import { errorMessage } from '../../core/errors/api-error';
import { shortId, WhatsappContextService } from '../../core/whatsapp/whatsapp';
import { cancellationText } from './booking-labels';

/**
 * Volta do Stripe: /checkout/:id/sucesso e /checkout/:id/cancelado.
 * Em "sucesso", consulta a reserva até o webhook confirmar (ou cancelar).
 */
@Component({
  selector: 'app-checkout-return-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink],
  template: `
    <div class="container stack narrow">
      @if (outcome() === 'cancelado') {
        <h1>Pagamento não concluído</h1>
        <p>
          Nada foi cobrado. Sua reserva continua guardada até o fim do prazo, se você quiser tentar
          de novo.
        </p>
        <p><a class="button" [routerLink]="['/checkout', id()]">Tentar de novo</a></p>
      } @else {
        <h1>{{ title() }}</h1>
        <div aria-live="polite" class="stack-tight">
          @if (error(); as message) {
            <p class="alert" role="alert">{{ message }}</p>
            <button type="button" class="button button--ghost" (click)="start()">
              Consultar de novo
            </button>
          } @else if (booking(); as b) {
            @switch (b.status) {
              @case ('CONFIRMED') {
                <p class="status status--ok">
                  Pagamento de {{ b.total | currency: b.currency }} confirmado. Reserva
                  <strong>{{ shortId(b.id) }}</strong> garantida! Enviamos os detalhes para o seu
                  e-mail.
                </p>
                <p class="actions">
                  <a class="button" routerLink="/minhas-viagens">Ver minhas viagens</a>
                  <a class="button button--ghost" routerLink="/">Voltar para o início</a>
                </p>
              }
              @case ('CANCELLED') {
                <p class="status status--warn">{{ cancellationText(b) }}</p>
              }
              @default {
                @if (b.payment?.status === 'FAILED') {
                  <p class="status status--warn">
                    O pagamento foi recusado. Você pode tentar de novo.
                  </p>
                  <p><a class="button" [routerLink]="['/checkout', id()]">Tentar de novo</a></p>
                } @else {
                  <p>
                    Estamos aguardando a confirmação do pagamento. Com Pix, pode levar alguns
                    instantes depois que você pagar no app do banco. Pode deixar esta página aberta.
                  </p>
                }
              }
            }
          } @else {
            <p class="muted" aria-busy="true">Consultando o pagamento…</p>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }
    .narrow {
      max-width: 44rem;
    }
    .stack-tight {
      display: grid;
      gap: var(--space-3);
    }
    .status {
      padding: var(--space-3);
      border-radius: var(--radius);
      border: 1px solid var(--color-border);
      background: var(--color-surface);
    }
    .status--ok {
      border-color: var(--color-success);
      background: #e9f7ee;
    }
    .status--warn {
      border-color: var(--color-accent);
      background: #fff3e6;
    }
  `,
})
export class CheckoutReturnPage {
  private readonly api = inject(CheckoutApi);
  private readonly whatsapp = inject(WhatsappContextService);
  protected readonly shortId = shortId;
  protected readonly cancellationText = cancellationText;

  readonly id = input.required<string>();
  /** Definido na rota: 'sucesso' ou 'cancelado'. */
  readonly outcome = input.required<'sucesso' | 'cancelado'>();

  protected readonly booking = signal<Booking | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly title = computed(() =>
    this.booking()?.status === 'CONFIRMED' ? 'Reserva confirmada!' : 'Confirmando seu pagamento',
  );

  private subscription: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.subscription?.unsubscribe());
    effect(() => {
      this.whatsapp.set({ kind: 'booking', id: this.id() });
      if (this.outcome() === 'sucesso') {
        untracked(() => this.start());
      }
    });
  }

  protected start(): void {
    this.subscription?.unsubscribe();
    this.error.set(null);
    this.subscription = pollBooking(() => this.api.booking(this.id())).subscribe({
      next: (booking) => this.booking.set(booking),
      error: (error: unknown) => this.error.set(errorMessage(error)),
    });
  }
}
