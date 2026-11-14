import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  CarRentalOffer,
  CarRentalOfferRequest,
  Category,
  City,
  Flight,
  FlightRequest,
  Hotel,
  HotelRequest,
  Page,
  RoomOffer,
  RoomOfferRequest,
  TravelPackage,
  TravelPackageRequest,
} from '../api/api-types';
import { APP_CONFIG } from '../config/app-config';
import { QueryValue, queryParams } from './query-params';

/** Recursos do catálogo em /api/admin (CRUD desde a Fase 2). */
export interface CatalogResources {
  flights: { item: Flight; request: FlightRequest };
  hotels: { item: Hotel; request: HotelRequest };
  'room-offers': { item: RoomOffer; request: RoomOfferRequest };
  'car-rental-offers': { item: CarRentalOffer; request: CarRentalOfferRequest };
  'travel-packages': { item: TravelPackage; request: TravelPackageRequest };
  cities: { item: City; request: never };
  categories: { item: Category; request: never };
}

export type CatalogResource = keyof CatalogResources;

/** Id de oferta do catálogo curado aceito pela cotação (docs/api/admin.yaml, ManualQuoteRequest). */
export function curatedOfferId(catalogId: string): string {
  return `curated:${catalogId}`;
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/api/admin`;

  list<R extends CatalogResource>(
    resource: R,
    params: Readonly<Record<string, QueryValue>> = {},
  ): Observable<Page<CatalogResources[R]['item']>> {
    return this.http.get<Page<CatalogResources[R]['item']>>(`${this.base}/${resource}`, {
      params: queryParams(params),
    });
  }

  get<R extends CatalogResource>(resource: R, id: string): Observable<CatalogResources[R]['item']> {
    return this.http.get<CatalogResources[R]['item']>(
      `${this.base}/${resource}/${encodeURIComponent(id)}`,
    );
  }

  create<R extends CatalogResource>(
    resource: R,
    request: CatalogResources[R]['request'],
  ): Observable<CatalogResources[R]['item']> {
    return this.http.post<CatalogResources[R]['item']>(`${this.base}/${resource}`, request);
  }

  update<R extends CatalogResource>(
    resource: R,
    id: string,
    request: CatalogResources[R]['request'],
  ): Observable<CatalogResources[R]['item']> {
    return this.http.put<CatalogResources[R]['item']>(
      `${this.base}/${resource}/${encodeURIComponent(id)}`,
      request,
    );
  }

  delete(resource: CatalogResource, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${resource}/${encodeURIComponent(id)}`);
  }
}
