import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import type { PaymentMethod } from '../../core/api/api-types';
import { CheckoutApi } from '../../core/checkout/checkout-api';
import { PaymentRedirect } from '../../core/checkout/payment-redirect';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { shortId, WhatsappContextService } from '../../core/whatsapp/whatsapp';
import { LoadError } from '../../shared/load-error';
import { ITEM_LABELS } from '../quote/quote.page';
import { cancellationText, METHOD_LABELS } from './booking-labels';

/** /checkout/:id — reserva PENDING_PAYMENT: escolhe o método e vai para o Stripe. */
@Component({
  selector: 'app-checkout-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, LoadError],
  templateUrl: './checkout.page.html',
  styleUrl: './checkout.page.scss',
})
export class CheckoutPage {
  private readonly api = inject(CheckoutApi);
  private readonly payment = inject(PaymentRedirect);
  private readonly router = inject(Router);
  private readonly whatsapp = inject(WhatsappContextService);
  protected readonly itemLabels = ITEM_LABELS;
  protected readonly methodLabels = METHOD_LABELS;
  protected readonly methods: PaymentMethod[] = ['PIX', 'CREDIT_CARD'];
  protected readonly shortId = shortId;
  protected readonly cancellationText = cancellationText;

  readonly id = input.required<string>();

  protected readonly method = signal<PaymentMethod>('PIX');
  protected readonly paying = signal(false);
  protected readonly payError = signal<string | null>(null);

  protected readonly booking = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.booking(params),
  });

  constructor() {
    effect(() => this.whatsapp.set({ kind: 'booking', id: this.id() }));
  }

  protected pay(): void {
    this.paying.set(true);
    this.payError.set(null);
    this.api.createCheckoutSession(this.id(), this.method()).subscribe({
      next: (session) => {
        try {
          this.payment.go(session.checkoutUrl);
        } catch (error) {
          console.error(error);
          this.paying.set(false);
          this.payError.set(errorMessage(error));
        }
      },
      error: (error: unknown) => {
        this.paying.set(false);
        if (error instanceof ApiError && error.code === 'PAYMENT_IN_PROGRESS') {
          void this.router.navigate(['/checkout', this.id(), 'sucesso']);
          return;
        }
        if (error instanceof ApiError && error.kind === 'unauthorized') return;
        this.payError.set(errorMessage(error));
        if (
          error instanceof ApiError &&
          (error.code === 'BOOKING_NOT_PAYABLE' || error.code === 'BOOKING_EXPIRED')
        ) {
          this.booking.reload();
        }
      },
    });
  }
}
