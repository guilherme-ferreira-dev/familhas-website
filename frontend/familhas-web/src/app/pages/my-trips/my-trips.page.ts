import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import type { MyRating, Trip, TripTiming } from '../../core/api/api-types';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { FavoritesStore } from '../../core/my-trips/favorites-store';
import { MyTripsApi } from '../../core/my-trips/my-trips-api';
import { shortId } from '../../core/whatsapp/whatsapp';
import { LoadError } from '../../shared/load-error';
import { PackageSummaryCard } from '../../shared/package-summary-card';
import { METHOD_LABELS } from '../checkout/booking-labels';
import { PAYMENT_STATUS_LABELS, RATING_OPTIONS, REASON_LABELS, tripBadge } from './my-trips-labels';

type Tab = 'proximas' | 'passadas';
const TIMING: Record<Tab, TripTiming> = { proximas: 'UPCOMING', passadas: 'PAST' };

/**
 * "Minhas viagens" (guia, Fase 8): viagens futuras e passadas com pagamento e parcelas, avaliações,
 * favoritos e recomendações. Tudo vem de /api/me/** (docs/api/minhas-viagens.yaml).
 */
@Component({
  selector: 'app-my-trips-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, LoadError, PackageSummaryCard],
  templateUrl: './my-trips.page.html',
  styleUrl: './my-trips.page.scss',
})
export class MyTripsPage {
  private readonly api = inject(MyTripsApi);
  protected readonly favoritesStore = inject(FavoritesStore);

  protected readonly methodLabels = METHOD_LABELS;
  protected readonly paymentLabels = PAYMENT_STATUS_LABELS;
  protected readonly reasonLabels = REASON_LABELS;
  protected readonly ratingOptions = RATING_OPTIONS;
  protected readonly tripBadge = tripBadge;
  protected readonly shortId = shortId;

  /** ?aba=proximas|passadas (withComponentInputBinding). */
  readonly aba = input<Tab, unknown>('proximas', {
    transform: (v: unknown) => (v === 'passadas' ? 'passadas' : 'proximas'),
  });

  protected readonly trips = rxResource({
    params: () => TIMING[this.aba()],
    stream: ({ params: when }) => this.api.trips(when),
  });
  protected readonly favorites = rxResource({ stream: () => this.api.favorites() });
  protected readonly ratings = rxResource({ stream: () => this.api.ratings() });
  protected readonly recommendations = rxResource({ stream: () => this.api.recommendations(6) });

  /** Favorito desmarcado some da lista na hora, sem recarregar. */
  protected readonly visibleFavorites = computed(() => {
    if (!this.favorites.hasValue()) return [];
    const list = this.favorites.value();
    return this.favoritesStore.ready() ? list.filter((p) => this.favoritesStore.has(p.id)) : list;
  });

  /** Nota escolhida em cada formulário de avaliação (viagem ou avaliação existente), por id. */
  protected readonly drafts = signal<Record<string, number>>({});
  protected readonly saving = signal<string | null>(null);
  protected readonly ratingError = signal<{ id: string; message: string } | null>(null);
  protected readonly status = signal('');

  protected draft(id: string, fallback = 5): number {
    return this.drafts()[id] ?? fallback;
  }

  protected setDraft(id: string, value: number): void {
    this.drafts.update((d) => ({ ...d, [id]: Number(value) }));
  }

  protected rateTrip(trip: Trip): void {
    if (!trip.travelPackage) return;
    const rating = this.draft(trip.id);
    this.run(
      trip.id,
      this.api.createRating({ rating, travelPackageId: trip.travelPackage.id }),
      () => this.status.set(`Avaliação de ${trip.title} enviada: nota ${rating} de 5.`),
    );
  }

  protected updateRating(rating: MyRating): void {
    const value = this.draft(rating.id, rating.rating);
    this.run(
      rating.id,
      this.api.updateRating(rating.id, { rating: value, travelPackageId: rating.travelPackage.id }),
      () => this.status.set(`Nota de ${rating.travelPackage.title} alterada para ${value}.`),
    );
  }

  protected deleteRating(rating: MyRating): void {
    this.run(rating.id, this.api.deleteRating(rating.id), () =>
      this.status.set(`Avaliação de ${rating.travelPackage.title} apagada.`),
    );
  }

  private run(id: string, request: Observable<unknown>, done: () => void): void {
    this.saving.set(id);
    this.ratingError.set(null);
    request.subscribe({
      next: () => {
        this.saving.set(null);
        done();
        this.trips.reload();
        this.ratings.reload();
        this.recommendations.reload();
      },
      error: (error: unknown) => {
        this.saving.set(null);
        if (error instanceof ApiError && error.kind === 'unauthorized') return;
        this.ratingError.set({ id, message: errorMessage(error) });
      },
    });
  }

  protected stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }
}
