import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  ClientRatingRequest,
  MyRating,
  MyRatingPage,
  PackageSummary,
  Recommendation,
  Trip,
  TripPage,
  TripTiming,
} from '../api/api-types';
import { APP_CONFIG } from '../config/app-config';

/** Viagens, favoritos, avaliações e recomendações do cliente logado (docs/api/minhas-viagens.yaml, Fase 8). */
@Injectable({ providedIn: 'root' })
export class MyTripsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/api/me`;

  trips(when: TripTiming, page = 0, size = 20): Observable<TripPage> {
    const params = new HttpParams().set('when', when).set('page', page).set('size', size);
    return this.http.get<TripPage>(`${this.base}/trips`, { params });
  }

  trip(id: string): Observable<Trip> {
    return this.http.get<Trip>(`${this.base}/trips/${encodeURIComponent(id)}`);
  }

  favorites(): Observable<PackageSummary[]> {
    return this.http.get<PackageSummary[]>(`${this.base}/favorites`);
  }

  addFavorite(packageId: string): Observable<void> {
    return this.http.put<void>(`${this.base}/favorites/${encodeURIComponent(packageId)}`, null);
  }

  removeFavorite(packageId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/favorites/${encodeURIComponent(packageId)}`);
  }

  ratings(page = 0, size = 20): Observable<MyRatingPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<MyRatingPage>(`${this.base}/ratings`, { params });
  }

  createRating(request: ClientRatingRequest): Observable<MyRating> {
    return this.http.post<MyRating>(`${this.base}/ratings`, request);
  }

  updateRating(id: string, request: ClientRatingRequest): Observable<MyRating> {
    return this.http.put<MyRating>(`${this.base}/ratings/${encodeURIComponent(id)}`, request);
  }

  deleteRating(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/ratings/${encodeURIComponent(id)}`);
  }

  recommendations(limit = 6): Observable<Recommendation[]> {
    return this.http.get<Recommendation[]>(`${this.base}/recommendations`, {
      params: new HttpParams().set('limit', limit),
    });
  }
}
