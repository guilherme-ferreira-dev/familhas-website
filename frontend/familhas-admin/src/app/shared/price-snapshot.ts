import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { PricedItem } from '../core/api/api-types';
import { formatDate, money, percent } from './format';
import { PRICE_ITEM } from './labels';

export interface Snapshot {
  readonly currency: string;
  readonly componentsTotal: number;
  readonly marginPercent: number;
  readonly marginAmount: number;
  readonly promotionPercent: number;
  readonly discountAmount: number;
  readonly serviceFeeAmount: number;
  readonly totalAmount: number;
  readonly items: readonly PricedItem[];
  /** Só na cotação: parte do desconto dada pelo admin. */
  readonly manualDiscountAmount?: number;
}

/** Itens com custo e composição do preço (custo + margem - descontos + taxa = total). Só no Admin. */
@Component({
  selector: 'app-price-snapshot',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let s = snapshot();
    <div class="table-wrap">
      <table>
        <caption class="visually-hidden">
          Itens e custos
        </caption>
        <thead>
          <tr>
            <th scope="col">Item</th>
            <th scope="col">Descrição</th>
            <th scope="col">Período</th>
            <th scope="col" class="num">Qtd.</th>
            <th scope="col" class="num">Custo unit.</th>
            <th scope="col" class="num">Custo</th>
            <th scope="col">Fornecedor</th>
          </tr>
        </thead>
        <tbody>
          @for (item of s.items; track $index) {
            <tr>
              <td>{{ itemLabel(item) }}</td>
              <td>{{ item.description }}</td>
              <td>{{ date(item.startDate) }} a {{ date(item.endDate) }}</td>
              <td class="num">{{ item.quantity }}</td>
              <td class="num">{{ money(item.unitPrice, item.currency) }}</td>
              <td class="num">{{ money(item.subtotal, item.currency) }}</td>
              <td>{{ item.provider }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    <dl class="breakdown card">
      <div>
        <dt>Custo dos itens</dt>
        <dd class="num">{{ money(s.componentsTotal, s.currency) }}</dd>
      </div>
      <div>
        <dt>Margem ({{ percent(s.marginPercent) }})</dt>
        <dd class="num">+ {{ money(s.marginAmount, s.currency) }}</dd>
      </div>
      @if (s.manualDiscountAmount !== undefined) {
        <div>
          <dt>Promoção ({{ percent(s.promotionPercent) }})</dt>
          <dd class="num">− {{ money(s.discountAmount - s.manualDiscountAmount, s.currency) }}</dd>
        </div>
        <div>
          <dt>Desconto manual</dt>
          <dd class="num">− {{ money(s.manualDiscountAmount, s.currency) }}</dd>
        </div>
      } @else {
        <div>
          <dt>Descontos (promoção {{ percent(s.promotionPercent) }})</dt>
          <dd class="num">− {{ money(s.discountAmount, s.currency) }}</dd>
        </div>
      }
      <div>
        <dt>Taxa de serviço</dt>
        <dd class="num">+ {{ money(s.serviceFeeAmount, s.currency) }}</dd>
      </div>
      <div class="total">
        <dt>Total</dt>
        <dd class="num">{{ money(s.totalAmount, s.currency) }}</dd>
      </div>
    </dl>
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-3);
    }
    .breakdown {
      display: grid;
      gap: var(--space-1);
      max-width: 26rem;
      margin: 0;
    }
    .breakdown div {
      display: flex;
      justify-content: space-between;
      gap: var(--space-3);
    }
    dd {
      margin: 0;
    }
    .total {
      border-top: 1px solid var(--color-border);
      padding-top: var(--space-1);
      font-weight: 700;
    }
  `,
})
export class PriceSnapshot {
  readonly snapshot = input.required<Snapshot>();
  protected readonly money = money;
  protected readonly percent = percent;
  protected readonly date = formatDate;
  protected itemLabel(item: PricedItem) {
    return PRICE_ITEM[item.type];
  }
}
