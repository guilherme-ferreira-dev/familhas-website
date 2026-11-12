import { computed, inject, Injectable } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import type { City } from '../api/api-types';
import { CatalogApi } from './catalog-api';

/**
 * Cidades ativas, carregadas uma vez por sessão. A busca e os cards recebem só o UUID da cidade;
 * aqui fica o nome para exibir e as listas de origem e destino dos filtros.
 */
@Injectable({ providedIn: 'root' })
export class CityDirectory {
  private readonly api = inject(CatalogApi);
  private readonly resource = rxResource({
    stream: () => this.api.cities().pipe(map((page) => page.content)),
  });

  readonly all = computed<City[]>(() => (this.resource.hasValue() ? this.resource.value() : []));
  readonly origins = computed(() => this.all().filter((c) => c.cityType === 'ORIGIN'));
  readonly destinations = computed(() => this.all().filter((c) => c.cityType === 'DESTINATION'));
  readonly isLoading = this.resource.isLoading;
  readonly error = this.resource.error;
  private readonly byId = computed(() => new Map(this.all().map((c) => [c.id, c])));

  name(id: string | null | undefined): string {
    if (!id) return '';
    return this.byId().get(id)?.name ?? '';
  }

  reload(): void {
    this.resource.reload();
  }
}
