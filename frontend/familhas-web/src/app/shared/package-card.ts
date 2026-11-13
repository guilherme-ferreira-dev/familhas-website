import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { PackageSearchResult } from '../core/api/api-types';
import { CityDirectory } from '../core/catalog/city-directory';

@Component({
  selector: 'app-package-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    @let pkg = item();
    <article class="card package">
      <div
        class="media dusk-media"
        [class]="'media dusk-media dusk-v' + variant()"
        aria-hidden="true"
      >
        @if (pkg.imageUrl) {
          <img [src]="pkg.imageUrl" alt="" loading="lazy" width="640" height="360" />
        }
      </div>
      <div class="body">
        <h3>
          <a [routerLink]="['/pacotes', pkg.id]" [queryParams]="{ passageiros: pkg.passengers }">{{
            pkg.title
          }}</a>
        </h3>
        <p class="muted">
          {{ cities.name(pkg.originCityId) }} → {{ cities.name(pkg.destinationCityId) }} ·
          {{ pkg.departureAt | date: 'dd/MM/yyyy' }}
          @if (pkg.returnAt) {
            a {{ pkg.returnAt | date: 'dd/MM/yyyy' }}
          }
        </p>
        <p class="price">
          @if (pkg.originalPricePerPerson) {
            <s class="muted">
              <span class="visually-hidden">De </span
              >{{ pkg.originalPricePerPerson | currency: pkg.currency }}</s
            >
            <span class="visually-hidden"> por </span>
          } @else {
            <span class="muted from">A partir de</span>
          }
          <strong>{{ pkg.pricePerPerson | currency: pkg.currency }}</strong>
          <span class="muted">por pessoa</span>
        </p>
        @if (pkg.seatsAvailable > 0 && pkg.seatsAvailable <= 5) {
          <p class="seats">Últimos {{ pkg.seatsAvailable }} lugares</p>
        }
      </div>
    </article>
  `,
  styles: `
    .package {
      display: grid;
      grid-template-rows: auto 1fr;
      align-content: start;
      padding: 0;
      overflow: hidden;
      position: relative;
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
    .body {
      display: grid;
      align-content: start;
      gap: var(--space-2);
      padding: var(--space-3);
    }
    h3 {
      font-family: var(--font-display);
      font-weight: 600;
      font-size: 1.375rem;
    }
    /* O card inteiro é clicável, mas o link continua sendo o título (bom para leitor de tela). */
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
    .package:has(a:focus-visible) {
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
      font-size: 1.375rem;
      color: var(--color-primary);
    }
    .from {
      width: 100%;
      font-size: 0.875rem;
    }
    .seats {
      color: var(--color-accent);
      font-weight: 600;
      font-size: 0.875rem;
    }
  `,
})
export class PackageCard {
  readonly item = input.required<PackageSearchResult>();
  protected readonly cities = inject(CityDirectory);
  /** Variante de cor da ilustração provisória, estável por pacote. */
  protected readonly variant = computed(() => {
    let h = 0;
    for (const ch of this.item().id) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    return h % 4;
  });
}
