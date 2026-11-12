import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  CarRentalOffer,
  CarSearchResult,
  CategoryPage,
  CityPage,
  Flight,
  FlightSearchResult,
  Hotel,
  HotelSearchResult,
  PackageSearchResult,
  RoomOffer,
  SearchPage,
  TravelPackage,
} from '../api/api-types';
import { APP_CONFIG } from '../config/app-config';

type QueryValue = string | number | null | undefined;
export type Query = Readonly<Record<string, QueryValue>>;

/** Acesso ao catálogo e à busca públicos (docs/api/familhas-api.yaml). */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/api`;

  cities(query: Query = {}): Observable<CityPage> {
    return this.http.get<CityPage>(`${this.base}/cities`, {
      params: toParams({ size: 100, ...query }),
    });
  }

  categories(query: Query = {}): Observable<CategoryPage> {
    return this.http.get<CategoryPage>(`${this.base}/categories`, {
      params: toParams({ size: 100, ...query }),
    });
  }

  travelPackage(id: string): Observable<TravelPackage> {
    return this.http.get<TravelPackage>(`${this.base}/travel-packages/${encodeURIComponent(id)}`);
  }

  flight(id: string): Observable<Flight> {
    return this.http.get<Flight>(`${this.base}/flights/${encodeURIComponent(id)}`);
  }

  hotel(id: string): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.base}/hotels/${encodeURIComponent(id)}`);
  }

  roomOffer(id: string): Observable<RoomOffer> {
    return this.http.get<RoomOffer>(`${this.base}/room-offers/${encodeURIComponent(id)}`);
  }

  carRentalOffer(id: string): Observable<CarRentalOffer> {
    return this.http.get<CarRentalOffer>(
      `${this.base}/car-rental-offers/${encodeURIComponent(id)}`,
    );
  }

  searchPackages(query: Query): Observable<SearchPage<PackageSearchResult>> {
    return this.search('packages', query);
  }

  searchFlights(query: Query): Observable<SearchPage<FlightSearchResult>> {
    return this.search('flights', query);
  }

  searchHotels(query: Query): Observable<SearchPage<HotelSearchResult>> {
    return this.search('hotels', query);
  }

  searchCars(query: Query): Observable<SearchPage<CarSearchResult>> {
    return this.search('cars', query);
  }

  private search<T>(kind: string, query: Query): Observable<SearchPage<T>> {
    return this.http.get<SearchPage<T>>(`${this.base}/search/${kind}`, { params: toParams(query) });
  }
}

/** Ignora filtros vazios: a API trata ausência como "sem filtro". */
export function toParams(query: Query): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
