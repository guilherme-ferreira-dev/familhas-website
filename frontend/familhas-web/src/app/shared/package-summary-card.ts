import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { PackageSummary } from '../core/api/api-types';
import { FavoriteButton } from './favorite-button';

/**
 * Cartão de pacote da área do cliente (favoritos e recomendações). O título é o link; o coração fica acima
 * da área clicável. Conteúdo extra (motivos da recomendação) entra por projeção.
 */
@Component({
  selector: 'app-package-summary-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, RouterLink, FavoriteButton],
  template: `
    @let pkg = item();
    <article class="card summary">
      <div [class]="'media dusk-media dusk-v' + variant()" aria-hidden="true">
        @if (pkg.imageUrl) {
          <img [src]="pkg.imageUrl" alt="" loading="lazy" width="640" height="360" />
        }
      </div>
      <div class="favorite">
        <app-favorite-button [packageId]="pkg.id" [packageTitle]="pkg.title" [compact]="true" />
      </div>
      <div class="body">
        <h3>
          @if (pkg.active) {
            <a [routerLink]="['/pacotes', pkg.id]">{{ pkg.title }}</a>
          } @else {
            {{ pkg.title }}
          }
        </h3>
        <p class="muted">
          {{ pkg.destinationCityName }}, {{ pkg.destinationCountry }}
          @if (pkg.departureAt) {
            · {{ pkg.departureAt | date: 'dd/MM/yyyy' }}
          }
        </p>
        @if (pkg.active) {
          <p class="price">
            <span class="muted">A partir de</span>
            <strong>{{ pkg.pricePerPerson | currency: pkg.currency }}</strong>
            <span class="muted">por pessoa</span>
          </p>
        } @else {
          <p class="muted">Saiu do catálogo</p>
        }
        <ng-content />
      </div>
    </article>
  `,
  styles: `
    .summary {
      position: relative;
      display: grid;
      grid-template-rows: auto 1fr;
      padding: 0;
      overflow: hidden;
      border: 0;
      box-shadow: var(--shadow);
      height: 100%;
    }
    .media {
      aspect-ratio: 16 / 9;
    }
    .media img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .favorite {
      position: absolute;
      top: var(--space-2);
      right: var(--space-2);
      z-index: 1;
    }
    .body {
      display: grid;
      align-content: start;
      gap: var(--space-2);
      padding: var(--space-3);
    }
    h3 {
      font-family: var(--font-display);
      font-weight: 600;
      font-size: 1.25rem;
    }
    h3 a {
      color: var(--color-text);
      text-decoration: none;
    }
    h3 a::after {
      content: '';
      position: absolute;
      inset: 0;
    }
    h3 a:focus-visible {
      outline: none;
    }
    .summary:has(h3 a:focus-visible) {
      outline: 3px solid var(--color-focus);
      outline-offset: 2px;
    }
    .price {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: var(--space-1) var(--space-2);
    }
    .price strong {
      font-size: 1.25rem;
      color: var(--color-primary);
    }
  `,
})
export class PackageSummaryCard {
  readonly item = input.required<PackageSummary>();
  protected readonly variant = computed(() => {
    let h = 0;
    for (const ch of this.item().id) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    return h % 4;
  });
}
