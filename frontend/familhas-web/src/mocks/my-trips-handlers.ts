import { http, HttpResponse } from 'msw';
import type {
  Booking,
  ClientRatingRequest,
  MyRating,
  PackageSummary,
  Recommendation,
  RecommendationReason,
  TravelPackage,
  Trip,
  TripPage,
  TripTiming,
} from '../app/core/api/api-types';
import { categories, cities, flights, travelPackages } from './fixtures';
import { authenticated, coded, problem, type MockState } from './handlers';

/**
 * Handlers da Fase 8 seguindo docs/api/minhas-viagens.yaml. Viagens = viagem passada fixa (fixture) + reservas
 * do mock que já foram confirmadas. Mesmas regras da API: avaliar só pacote de viagem passada, favoritos
 * idempotentes e recomendação que não repete pacote reservado, favoritado ou avaliado.
 */
export function createMyTripsHandlers(state: MockState, wait: () => Promise<unknown>) {
  return [
    http.get(
      '*/api/me/trips',
      authenticated(async ({ request }) => {
        await wait();
        const params = new URL(request.url).searchParams;
        const when = (params.get('when') ?? 'UPCOMING') as TripTiming;
        if (when !== 'UPCOMING' && when !== 'PAST') {
          return problem(request, 400, 'Bad Request', 'Parametro when invalido.');
        }
        const page = Number(params.get('page') ?? 0);
        const size = Number(params.get('size') ?? 20);
        const trips = allTrips(state)
          .filter((t) => t.timing === when)
          .sort((a, b) =>
            when === 'UPCOMING'
              ? (a.startDate ?? '').localeCompare(b.startDate ?? '')
              : (b.endDate ?? '').localeCompare(a.endDate ?? ''),
          );
        const body: TripPage = {
          content: trips.slice(page * size, page * size + size),
          page,
          size,
          totalElements: trips.length,
          totalPages: Math.ceil(trips.length / size),
        };
        return HttpResponse.json(body);
      }),
    ),

    http.get(
      '*/api/me/trips/:id',
      authenticated(async ({ params, request }) => {
        await wait();
        const trip = allTrips(state).find((t) => t.id === String(params['id']));
        return trip
          ? HttpResponse.json(trip)
          : coded(request, 404, 'TRIP_NOT_FOUND', 'Viagem nao encontrada.');
      }),
    ),

    http.get(
      '*/api/me/favorites',
      authenticated(async () => {
        await wait();
        const list = travelPackages
          .filter((p) => state.favorites.has(p.id))
          .map(summaryOf)
          .sort((a, b) => a.title.localeCompare(b.title, 'pt-BR'));
        return HttpResponse.json(list);
      }),
    ),

    http.put(
      '*/api/me/favorites/:packageId',
      authenticated(async ({ params, request }) => {
        await wait();
        const id = String(params['packageId']);
        const pkg = travelPackages.find((p) => p.id === id && p.status === 'ACTIVE');
        if (!pkg) return coded(request, 404, 'PACKAGE_NOT_FOUND', 'Pacote nao encontrado.');
        state.favorites.add(id);
        state.save();
        return new HttpResponse(null, { status: 204 });
      }),
    ),

    http.delete(
      '*/api/me/favorites/:packageId',
      authenticated(async ({ params }) => {
        await wait();
        state.favorites.delete(String(params['packageId']));
        state.save();
        return new HttpResponse(null, { status: 204 });
      }),
    ),

    http.get(
      '*/api/me/ratings',
      authenticated(async ({ request }) => {
        await wait();
        const params = new URL(request.url).searchParams;
        const page = Number(params.get('page') ?? 0);
        const size = Number(params.get('size') ?? 20);
        const list = [...state.ratings.values()].sort((a, b) =>
          (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
        );
        return HttpResponse.json({
          content: list.slice(page * size, page * size + size),
          page,
          size,
          totalElements: list.length,
          totalPages: Math.ceil(list.length / size),
        });
      }),
    ),

    http.post(
      '*/api/me/ratings',
      authenticated(async ({ request }) => {
        await wait();
        const body = (await request.json()) as ClientRatingRequest;
        const invalid = invalidRating(request, body);
        if (invalid) return invalid;
        if (!traveledWith(state, body.travelPackageId)) {
          return coded(request, 422, 'RATING_NOT_ALLOWED', 'Pacote sem viagem passada.');
        }
        if ([...state.ratings.values()].some((r) => r.travelPackage.id === body.travelPackageId)) {
          return coded(request, 409, 'RATING_ALREADY_EXISTS', 'Pacote ja avaliado.');
        }
        const now = new Date().toISOString();
        const rating: MyRating = {
          id: crypto.randomUUID(),
          rating: body.rating,
          travelPackage: summaryOf(packageById(body.travelPackageId)!),
          createdAt: now,
          updatedAt: now,
        };
        state.ratings.set(rating.id, rating);
        state.save();
        return HttpResponse.json(rating, { status: 201 });
      }),
    ),

    http.put(
      '*/api/me/ratings/:id',
      authenticated(async ({ params, request }) => {
        await wait();
        const existing = state.ratings.get(String(params['id']));
        if (!existing) return coded(request, 404, 'RATING_NOT_FOUND', 'Avaliacao nao encontrada.');
        const body = (await request.json()) as ClientRatingRequest;
        const invalid = invalidRating(request, body);
        if (invalid) return invalid;
        if (
          body.travelPackageId !== existing.travelPackage.id &&
          !traveledWith(state, body.travelPackageId)
        ) {
          return coded(request, 422, 'RATING_NOT_ALLOWED', 'Pacote sem viagem passada.');
        }
        const updated: MyRating = {
          ...existing,
          rating: body.rating,
          travelPackage: summaryOf(packageById(body.travelPackageId)!),
          updatedAt: new Date().toISOString(),
        };
        state.ratings.set(updated.id, updated);
        state.save();
        return HttpResponse.json(updated);
      }),
    ),

    http.delete(
      '*/api/me/ratings/:id',
      authenticated(async ({ params, request }) => {
        await wait();
        if (!state.ratings.delete(String(params['id']))) {
          return coded(request, 404, 'RATING_NOT_FOUND', 'Avaliacao nao encontrada.');
        }
        state.save();
        return new HttpResponse(null, { status: 204 });
      }),
    ),

    http.get(
      '*/api/me/recommendations',
      authenticated(async ({ request }) => {
        await wait();
        const limit = Number(new URL(request.url).searchParams.get('limit') ?? 6);
        if (!Number.isInteger(limit) || limit < 1 || limit > 20) {
          return problem(request, 400, 'Bad Request', 'limit deve ficar entre 1 e 20.');
        }
        return HttpResponse.json(recommend(state).slice(0, limit));
      }),
    ),
  ];
}

/** Viagem já realizada do cliente de teste (Porto Seguro), paga no Pix. */
export const PAST_TRIP_ID = '0e1d0000-0000-4000-8000-000000000001';

function pastTrip(): Trip {
  const pkg = travelPackages[1];
  return {
    id: PAST_TRIP_ID,
    status: 'COMPLETED',
    timing: 'PAST',
    inProgress: false,
    startDate: '2026-07-05',
    endDate: '2026-07-12',
    title: pkg.title,
    passengers: 3,
    travelPackage: summaryOf(pkg),
    items: [
      {
        type: 'FLIGHT',
        description: 'Gol - GRU / BPS',
        startDate: '2026-07-05',
        endDate: '2026-07-12',
        quantity: 3,
      },
    ],
    currency: 'BRL',
    total: 8940,
    confirmedAt: '2026-05-20T14:10:00Z',
    payment: {
      method: 'PIX',
      status: 'SUCCEEDED',
      installments: 1,
      amount: 8940,
      currency: 'BRL',
      refundedAmount: 0,
      paidAt: '2026-05-20T14:10:00Z',
      installmentPlan: [{ number: 1, amount: 8940, currency: 'BRL' }],
    },
    myRating: null,
    canRate: true,
  };
}

function allTrips(state: MockState): Trip[] {
  const confirmed = [...state.bookings.values()]
    .filter((b) => b.status === 'CONFIRMED' || b.status === 'COMPLETED')
    .map(tripOf);
  return [pastTrip(), ...confirmed].map((trip) => withRating(trip, state));
}

function withRating(trip: Trip, state: MockState): Trip {
  const mine = trip.travelPackage
    ? [...state.ratings.values()].find((r) => r.travelPackage.id === trip.travelPackage!.id)
    : undefined;
  return {
    ...trip,
    myRating: mine ? { id: mine.id, rating: mine.rating } : null,
    canRate: trip.timing === 'PAST' && !!trip.travelPackage && !mine,
  };
}

function tripOf(booking: Booking): Trip {
  const starts = booking.items.map((i) => i.startDate).sort();
  const ends = booking.items.map((i) => i.endDate).sort();
  const start = starts[0] ?? null;
  const end = ends[ends.length - 1] ?? null;
  const today = new Date().toISOString().slice(0, 10);
  const timing: TripTiming =
    booking.status === 'COMPLETED' || (end !== null && end < today) ? 'PAST' : 'UPCOMING';
  const pkg = booking.travelPackageId ? packageById(booking.travelPackageId) : undefined;
  const payment = booking.payment;
  return {
    id: booking.id,
    status: booking.status === 'COMPLETED' ? 'COMPLETED' : 'CONFIRMED',
    timing,
    inProgress: timing === 'UPCOMING' && start !== null && start <= today,
    startDate: start,
    endDate: end,
    title: pkg?.title ?? booking.items[0]?.description ?? 'Viagem',
    passengers: booking.passengers,
    travelPackage: pkg ? summaryOf(pkg) : null,
    items: booking.items,
    currency: booking.currency,
    total: booking.total,
    confirmedAt: booking.confirmedAt,
    payment: payment
      ? {
          method: payment.method,
          status: payment.status,
          installments: payment.installments,
          amount: payment.amount,
          currency: payment.currency,
          refundedAmount: payment.refundedAmount ?? 0,
          paidAt: payment.paidAt ?? null,
          installmentPlan: [{ number: 1, amount: payment.amount, currency: payment.currency }],
        }
      : null,
    myRating: null,
    canRate: false,
  };
}

function traveledWith(state: MockState, packageId: string): boolean {
  return allTrips(state).some((t) => t.timing === 'PAST' && t.travelPackage?.id === packageId);
}

function invalidRating(request: Request, body: ClientRatingRequest): Response | null {
  const errors: Record<string, string> = {};
  if (!Number.isInteger(body.rating) || body.rating < 1 || body.rating > 5) {
    errors['rating'] = 'must be between 1 and 5';
  }
  if (!body.travelPackageId) errors['travelPackageId'] = 'must not be null';
  return Object.keys(errors).length
    ? problem(request, 400, 'Validation failed', 'Um ou mais campos sao invalidos.', { errors })
    : null;
}

/** Versão simplificada da v1 por regras: mesma categoria ou país das viagens e favoritos, mais promoção. */
function recommend(state: MockState): Recommendation[] {
  const trips = allTrips(state);
  const seen = new Set<string>([
    ...state.favorites,
    ...[...state.ratings.values()].map((r) => r.travelPackage.id),
    ...trips.flatMap((t) => (t.travelPackage ? [t.travelPackage.id] : [])),
    ...[...state.bookings.values()].flatMap((b) =>
      b.status !== 'CANCELLED' && b.travelPackageId ? [b.travelPackageId] : [],
    ),
  ]);
  const liked = [
    ...trips.flatMap((t) => (t.travelPackage ? [t.travelPackage] : [])),
    ...travelPackages.filter((p) => state.favorites.has(p.id)).map(summaryOf),
  ];
  const likedCategories = new Set(liked.map((p) => p.categoryId));
  const likedCountries = new Set(liked.map((p) => p.destinationCountry));

  return travelPackages
    .filter((p) => p.status === 'ACTIVE' && !seen.has(p.id))
    .map((p) => {
      const summary = summaryOf(p);
      const reasons: RecommendationReason[] = [];
      let score = 0;
      if (likedCategories.has(summary.categoryId)) {
        score += 3;
        reasons.push('SIMILAR_CATEGORY');
      }
      if (likedCountries.has(summary.destinationCountry)) {
        score += 2;
        reasons.push('SIMILAR_DESTINATION');
      }
      if (summary.pricePerPerson < summary.originalPricePerPerson) {
        score += 0.5;
        reasons.push('ON_PROMOTION');
      }
      return { travelPackage: summary, score, reasons };
    })
    .sort(
      (a, b) => b.score - a.score || a.travelPackage.title.localeCompare(b.travelPackage.title),
    );
}

function packageById(id: string): TravelPackage | undefined {
  return travelPackages.find((p) => p.id === id);
}

export function summaryOf(pkg: TravelPackage): PackageSummary {
  const flight = flights.find((f) => f.id === pkg.flightId);
  const destination = cities.find((c) => c.id === pkg.destinationCityId);
  const category = categories.find((c) => c.id === pkg.categoryId);
  const onPromotion = pkg.pricePromotion != null && pkg.pricePromotion < pkg.price;
  return {
    id: pkg.id,
    title: pkg.title,
    imageUrl: pkg.imageUrl ?? null,
    categoryId: pkg.categoryId,
    categoryTitle: category?.title ?? '',
    destinationCityId: pkg.destinationCityId ?? '',
    destinationCityName: destination?.name ?? '',
    destinationCountry: destination?.country ?? '',
    departureAt: flight?.boardingDate ?? null,
    returnAt: flight?.returnDate ?? null,
    pricePerPerson: onPromotion ? pkg.pricePromotion! : pkg.price,
    originalPricePerPerson: pkg.price,
    currency: pkg.currency,
    active: pkg.status === 'ACTIVE',
  };
}
