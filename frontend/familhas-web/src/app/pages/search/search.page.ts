import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map, Observable } from 'rxjs';
import type {
  BoardType,
  CarCategory,
  CarSearchResult,
  FlightSearchResult,
  HotelSearchResult,
  PackageSearchResult,
  SearchPage as ResultPage,
} from '../../core/api/api-types';
import { CatalogApi } from '../../core/catalog/catalog-api';
import { CityDirectory } from '../../core/catalog/city-directory';
import { LoadError } from '../../shared/load-error';
import { PackageCard } from '../../shared/package-card';
import {
  filtersFrom,
  filtersToParams,
  invalidFields,
  KIND_LABELS,
  missingFields,
  SEARCH_KINDS,
  SearchFilters,
  SearchKind,
  toApiQuery,
} from './search-params';

type Results =
  | { kind: 'pacotes'; page: ResultPage<PackageSearchResult> }
  | { kind: 'voos'; page: ResultPage<FlightSearchResult> }
  | { kind: 'hoteis'; page: ResultPage<HotelSearchResult> }
  | { kind: 'carros'; page: ResultPage<CarSearchResult> };

const DATE_LABELS: Record<SearchKind, [string, string]> = {
  pacotes: ['Embarque a partir de', 'Embarque até'],
  voos: ['Ida', 'Volta (opcional)'],
  hoteis: ['Entrada', 'Saída'],
  carros: ['Retirada', 'Devolução'],
};

export const BOARD_LABELS: Record<BoardType, string> = {
  ROOM_ONLY: 'Só hospedagem',
  BREAKFAST: 'Café da manhã',
  HALF_BOARD: 'Meia pensão',
  FULL_BOARD: 'Pensão completa',
  ALL_INCLUSIVE: 'Tudo incluído',
};

export const CAR_LABELS: Record<CarCategory, string> = {
  ECONOMY: 'Econômico',
  COMPACT: 'Compacto',
  SEDAN: 'Sedã',
  SUV: 'SUV',
  MINIVAN: 'Minivan',
  LUXURY: 'Luxo',
};

@Component({
  selector: 'app-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CurrencyPipe, DatePipe, LoadError, PackageCard],
  templateUrl: './search.page.html',
  styleUrl: './search.page.scss',
})
export class SearchPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  protected readonly cities = inject(CityDirectory);
  private readonly resultsHeading = viewChild<ElementRef<HTMLElement>>('resultsHeading');

  protected readonly kinds = SEARCH_KINDS;
  protected readonly kindLabels = KIND_LABELS;
  protected readonly boardLabels = BOARD_LABELS;
  protected readonly carLabels = CAR_LABELS;
  protected readonly carCategories = Object.keys(CAR_LABELS) as CarCategory[];
  protected readonly passengerOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9];
  protected readonly today = new Date().toISOString().slice(0, 10);

  /** Filtros aplicados (URL). */
  protected readonly filters = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map(filtersFrom)),
    {
      requireSync: true,
    },
  );
  /** Rascunho do formulário; volta ao que está na URL quando ela muda. */
  protected readonly draft = linkedSignal(() => ({ ...this.filters() }));
  protected readonly submitted = signal(false);

  protected readonly dateLabels = computed(() => DATE_LABELS[this.draft().tipo]);
  protected readonly errors = computed(() => (this.submitted() ? invalidFields(this.draft()) : {}));
  protected readonly missing = computed(() => missingFields(this.filters()));

  protected readonly categories = rxResource({
    stream: () => this.api.categories().pipe(map((p) => p.content)),
  });

  protected readonly results = rxResource({
    params: () => {
      const f = this.filters();
      const ready = missingFields(f).length === 0 && Object.keys(invalidFields(f)).length === 0;
      return ready ? f : undefined;
    },
    stream: ({ params }) => this.search(params),
  });

  protected readonly summary = computed(() => {
    if (!this.results.hasValue()) return '';
    const { totalElements } = this.results.value().page;
    if (totalElements === 0) return 'Nenhum resultado para esses filtros.';
    return totalElements === 1
      ? '1 resultado encontrado.'
      : `${totalElements} resultados encontrados.`;
  });

  protected patch<K extends keyof SearchFilters>(key: K, value: SearchFilters[K]): void {
    this.draft.update((d) => ({ ...d, [key]: value }));
  }

  protected tabParams(kind: SearchKind) {
    const f = this.filters();
    return filtersToParams({
      ...f,
      tipo: kind,
      pagina: 1,
      estrelas: '',
      categoriaCarro: '',
      categoria: '',
    });
  }

  protected submit(): void {
    this.submitted.set(true);
    if (Object.keys(invalidFields(this.draft())).length > 0) {
      return;
    }
    this.submitted.set(false);
    void this.router.navigate([], { queryParams: filtersToParams({ ...this.draft(), pagina: 1 }) });
  }

  protected goToPage(page: number): void {
    void this.router
      .navigate([], { queryParams: filtersToParams({ ...this.filters(), pagina: page }) })
      .then(() => this.resultsHeading()?.nativeElement.focus());
  }

  private search(f: SearchFilters): Observable<Results> {
    const query = toApiQuery(f);
    switch (f.tipo) {
      case 'pacotes':
        return this.api
          .searchPackages(query)
          .pipe(map((page) => ({ kind: 'pacotes' as const, page })));
      case 'voos':
        return this.api.searchFlights(query).pipe(map((page) => ({ kind: 'voos' as const, page })));
      case 'hoteis':
        return this.api
          .searchHotels(query)
          .pipe(map((page) => ({ kind: 'hoteis' as const, page })));
      case 'carros':
        return this.api.searchCars(query).pipe(map((page) => ({ kind: 'carros' as const, page })));
    }
  }
}
