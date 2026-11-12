import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { Router, RouterLink } from '@angular/router';
import type { QuoteItem } from '../../core/api/api-types';
import { CheckoutApi } from '../../core/checkout/checkout-api';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { WhatsappContextService, shortId } from '../../core/whatsapp/whatsapp';
import { LoadError } from '../../shared/load-error';

export const ITEM_LABELS: Record<QuoteItem['type'], string> = {
  FLIGHT: 'Voo',
  HOTEL_STAY: 'Hospedagem',
  CAR_RENTAL: 'Carro',
};

@Component({
  selector: 'app-quote-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, RouterLink, LoadError],
  templateUrl: './quote.page.html',
  styleUrl: './quote.page.scss',
})
export class QuotePage {
  private readonly api = inject(CheckoutApi);
  private readonly whatsapp = inject(WhatsappContextService);
  private readonly router = inject(Router);
  protected readonly itemLabels = ITEM_LABELS;
  protected readonly shortId = shortId;

  readonly id = input.required<string>();

  protected readonly quote = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.quote(params),
  });

  protected readonly reserving = signal(false);
  protected readonly bookingError = signal<string | null>(null);

  protected readonly isOpen = computed(
    () => this.quote.hasValue() && this.quote.value().status === 'OPEN',
  );

  constructor() {
    effect(() => {
      if (this.quote.hasValue()) {
        const q = this.quote.value();
        this.whatsapp.set({ kind: 'quote', id: q.id, total: q.total, currency: q.currency });
      }
    });
  }

  /**
   * Cria a reserva (PENDING_PAYMENT) e abre o checkout. Se a cotação já virou reserva (409
   * QUOTE_ALREADY_BOOKED), a API devolve o bookingId e seguimos para ela em vez de criar outra.
   */
  protected reserve(): void {
    this.reserving.set(true);
    this.bookingError.set(null);
    this.api.createBooking(this.id()).subscribe({
      next: (booking) => void this.router.navigate(['/checkout', booking.id]),
      error: (error: unknown) => {
        const existing = error instanceof ApiError ? error.extra['bookingId'] : undefined;
        if (
          error instanceof ApiError &&
          error.code === 'QUOTE_ALREADY_BOOKED' &&
          typeof existing === 'string'
        ) {
          void this.router.navigate(['/checkout', existing]);
          return;
        }
        this.reserving.set(false);
        if (error instanceof ApiError && error.kind === 'unauthorized') return;
        this.bookingError.set(errorMessage(error));
        if (error instanceof ApiError && error.code === 'QUOTE_EXPIRED') {
          this.quote.reload();
        }
      },
    });
  }
}
