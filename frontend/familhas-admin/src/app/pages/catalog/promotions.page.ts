import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import type { TravelPackage } from '../../core/api/api-types';
import { CatalogApi } from '../../core/admin/catalog-api';
import { money, percent } from '../../shared/format';
import { ENTITY_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { StatusBadge } from '../../shared/status-badge';

/** Pacotes em promoção (pricePromotion menor que price). A promoção se edita no próprio pacote. */
@Component({
  selector: 'app-promotions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, StatusBadge, LoadError],
  template: `
    <div class="page-header">
      <div>
        <h2>Promoções</h2>
        <p>
          A promoção é o preço promocional do pacote. Na cotação ela vira o desconto percentual
          equivalente sobre custo + margem. Para criar ou tirar uma promoção, edite o pacote.
        </p>
      </div>
    </div>
    @if (packages.error()) {
      <app-load-error [error]="packages.error()" (retry)="packages.reload()" />
    }
    <div class="table-wrap">
      <table>
        <caption class="visually-hidden">
          Pacotes em promoção
        </caption>
        <thead>
          <tr>
            <th scope="col">Pacote</th>
            <th scope="col" class="num">Preço</th>
            <th scope="col" class="num">Promoção</th>
            <th scope="col" class="num">Desconto</th>
            <th scope="col">Status</th>
            <th scope="col"><span class="visually-hidden">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          @for (p of rows(); track p.id) {
            <tr>
              <td>{{ p.title }}</td>
              <td class="num">{{ money(p.price, p.currency) }}</td>
              <td class="num">{{ money(p.pricePromotion ?? 0, p.currency) }}</td>
              <td class="num">{{ percent(discount(p)) }}</td>
              <td><app-status-badge [label]="status(p).label" [tone]="status(p).tone" /></td>
              <td>
                <a class="btn btn--small" [routerLink]="['/catalogo', 'pacotes', p.id]"
                  >Editar pacote</a
                >
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="empty">Nenhum pacote em promoção.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-3);
    }
    p {
      max-width: 60rem;
    }
  `,
})
export class PromotionsPage {
  private readonly api = inject(CatalogApi);
  protected readonly money = money;
  protected readonly percent = percent;

  protected readonly packages = rxResource({
    stream: () =>
      this.api.list('travel-packages', { onPromotion: true, size: 100, sort: 'title,asc' }),
  });
  protected readonly rows = computed(() => this.packages.value()?.content ?? []);

  protected discount(p: TravelPackage): number {
    return p.price > 0 && p.pricePromotion !== null && p.pricePromotion !== undefined
      ? ((p.price - p.pricePromotion) / p.price) * 100
      : 0;
  }

  protected status(p: TravelPackage) {
    return ENTITY_STATUS[p.status];
  }
}
