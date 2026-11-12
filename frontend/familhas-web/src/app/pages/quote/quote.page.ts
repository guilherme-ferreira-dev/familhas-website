import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import type { QuoteItem } from '../../core/api/api-types';
import { CheckoutApi } from '../../core/checkout/checkout-api';
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
  protected readonly itemLabels = ITEM_LABELS;
  protected readonly shortId = shortId;

  readonly id = input.required<string>();

  protected readonly quote = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.quote(params),
  });

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
}
