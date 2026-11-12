import { delay, http, HttpResponse, type HttpResponseResolver } from 'msw';
import type {
  Booking,
  BookingRequest,
  ProblemCode,
  CarSearchResult,
  CheckoutSession,
  CheckoutSessionRequest,
  FlightSearchResult,
  HotelSearchResult,
  PackageSearchResult,
  ProblemDetail,
  Quote,
  QuoteRequest,
  SearchPage,
} from '../app/core/api/api-types';
import {
  carOffers,
  categories,
  cities,
  flights,
  hotels,
  me,
  roomOffers,
  travelPackages,
} from './fixtures';
import { MOCK_TOKEN_PREFIX } from './mock-token';

/**
 * Handlers do MSW seguindo docs/api/familhas-api.yaml. Valem para qualquer host (`*`),
 * então funcionam com qualquer apiBaseUrl do config.mock.json.
 *
 * Cenários especiais para testar erros na tela:
 * - busca de voos com passengers=9 -> 503 (provedor fora do ar)
 * - cotação com passengers=8 -> 422 (combinação não precificável)
 */
const SERVICE_FEE_RATE = 0.05;
const QUOTE_VALIDITY_MS = 24 * 60 * 60 * 1000;
const BOOKING_PAYMENT_WINDOW_MS = 30 * 60 * 1000;

export interface MockState {
  quotes: Map<string, Quote>;
  bookings: Map<string, Booking>;
  /** Quantas vezes cada reserva com checkout aberto já foi consultada (simula o webhook). */
  polls: Map<string, number>;
  /** Chamado depois de cada alteração (o build mock grava no sessionStorage). */
  save: () => void;
}

export interface MockSnapshot {
  quotes: [string, Quote][];
  bookings: [string, Booking][];
  polls: [string, number][];
}

export function createMockState(
  snapshot?: MockSnapshot | null,
  onSave?: (snapshot: MockSnapshot) => void,
): MockState {
  const state: MockState = {
    quotes: new Map(snapshot?.quotes ?? []),
    bookings: new Map(snapshot?.bookings ?? []),
    polls: new Map(snapshot?.polls ?? []),
    save: () =>
      onSave?.({
        quotes: [...state.quotes],
        bookings: [...state.bookings],
        polls: [...state.polls],
      }),
  };
  return state;
}

export function createHandlers(state: MockState = createMockState(), latencyMs = 300) {
  const wait = () => delay(latencyMs);

  return [
    http.get('*/api/cities', async ({ request }) => {
      await wait();
      const params = new URL(request.url).searchParams;
      const q = normalize(params.get('q') ?? '');
      const type = params.get('cityType');
      const filtered = cities.filter(
        (c) => (!q || normalize(c.name).includes(q)) && (!type || c.cityType === type),
      );
      return HttpResponse.json(paginate(filtered, params));
    }),

    http.get('*/api/categories', async ({ request }) => {
      await wait();
      return HttpResponse.json(paginate(categories, new URL(request.url).searchParams));
    }),

    http.get('*/api/travel-packages/:id', async ({ params, request }) => {
      await wait();
      return findOr404(travelPackages, String(params['id']), request);
    }),
    http.get('*/api/flights/:id', async ({ params, request }) => {
      await wait();
      return findOr404(flights, String(params['id']), request);
    }),
    http.get('*/api/hotels/:id', async ({ params, request }) => {
      await wait();
      return findOr404(hotels, String(params['id']), request);
    }),
    http.get('*/api/room-offers/:id', async ({ params, request }) => {
      await wait();
      return findOr404(roomOffers, String(params['id']), request);
    }),
    http.get('*/api/car-rental-offers/:id', async ({ params, request }) => {
      await wait();
      return findOr404(carOffers, String(params['id']), request);
    }),

    http.get('*/api/search/packages', async ({ request }) => {
      await wait();
      const params = new URL(request.url).searchParams;
      const passengers = intParam(params, 'passengers', 1);
      const results: PackageSearchResult[] = travelPackages
        .map((p) => {
          const flight = flights.find((f) => f.id === p.flightId)!;
          const pricePerPerson = p.pricePromotion ?? p.price;
          return {
            id: p.id,
            title: p.title,
            imageUrl: p.imageUrl,
            categoryId: p.categoryId,
            originCityId: p.originCityId,
            destinationCityId: p.destinationCityId,
            flightId: p.flightId,
            departureAt: flight.boardingDate,
            returnAt: flight.returnDate,
            passengers,
            pricePerPerson,
            originalPricePerPerson: p.pricePromotion != null ? p.price : null,
            totalPrice: round(pricePerPerson * passengers),
            currency: p.currency,
            seatsAvailable: flight.seatsAvailable ?? 0,
          };
        })
        .filter(
          (r) =>
            matches(params, 'originCityId', r.originCityId) &&
            matches(params, 'destinationCityId', r.destinationCityId) &&
            matches(params, 'categoryId', r.categoryId) &&
            dateFrom(params, 'departureFrom', r.departureAt) &&
            dateTo(params, 'departureTo', r.departureAt) &&
            inPriceRange(params, r.totalPrice) &&
            r.seatsAvailable >= passengers,
        );
      return HttpResponse.json(searchPage(results, params));
    }),

    http.get('*/api/search/flights', async ({ request }) => {
      await wait();
      const params = new URL(request.url).searchParams;
      const invalid = requireParams(request, params, [
        'originCityId',
        'destinationCityId',
        'departureDate',
      ]);
      if (invalid) return invalid;
      const passengers = intParam(params, 'passengers', 1);
      if (passengers === 9) {
        return problem(request, 503, 'Service Unavailable', 'Provedor de voos indisponível.', {
          provider: 'mock',
        });
      }
      const results: FlightSearchResult[] = flights
        .filter(
          (f) =>
            f.originCityId === params.get('originCityId') &&
            f.destinationCityId === params.get('destinationCityId') &&
            (f.seatsAvailable ?? 0) >= passengers,
        )
        .map((f) => ({
          offerId: `curated:flight:${f.id}`,
          provider: 'curated',
          companyName: f.companyName,
          originCityId: f.originCityId,
          destinationCityId: f.destinationCityId,
          departureAt: f.boardingDate,
          returnAt: params.get('returnDate') ? f.returnDate : null,
          boardingAirport: f.boardingAirport,
          returnAirport: f.returnAirport,
          passengers,
          pricePerPassenger: f.pricePerPassenger,
          totalPrice: round(f.pricePerPassenger * passengers),
          currency: f.currency,
          seatsAvailable: f.seatsAvailable ?? 0,
        }))
        .filter((r) => inPriceRange(params, r.totalPrice));
      return HttpResponse.json(searchPage(results, params));
    }),

    http.get('*/api/search/hotels', async ({ request }) => {
      await wait();
      const params = new URL(request.url).searchParams;
      const invalid = requireParams(request, params, ['cityId', 'checkIn', 'checkOut']);
      if (invalid) return invalid;
      const nights = daysBetween(params.get('checkIn')!, params.get('checkOut')!);
      const guests = intParam(params, 'guests', 1);
      const minStars = intParam(params, 'minStars', 1);
      const results: HotelSearchResult[] = roomOffers
        .map((room) => ({ room, hotel: hotels.find((h) => h.id === room.hotelId)! }))
        .filter(
          ({ hotel }) => hotel.cityId === params.get('cityId') && (hotel.stars ?? 0) >= minStars,
        )
        .map(({ room, hotel }) => {
          const rooms = Math.ceil(guests / (room.maxGuests ?? 1));
          return {
            offerId: `curated:room:${room.id}`,
            provider: 'curated',
            hotelName: hotel.name,
            cityId: hotel.cityId,
            stars: hotel.stars ?? 0,
            roomType: room.roomType,
            boardType: room.boardType,
            maxGuests: room.maxGuests ?? 1,
            rooms,
            nights,
            pricePerNight: room.pricePerNight,
            totalPrice: round(room.pricePerNight * rooms * nights),
            currency: room.currency,
          };
        })
        .filter((r) => inPriceRange(params, r.totalPrice));
      return HttpResponse.json(searchPage(results, params));
    }),

    http.get('*/api/search/cars', async ({ request }) => {
      await wait();
      const params = new URL(request.url).searchParams;
      const invalid = requireParams(request, params, ['cityId', 'pickUpDate', 'dropOffDate']);
      if (invalid) return invalid;
      const days = Math.max(1, daysBetween(params.get('pickUpDate')!, params.get('dropOffDate')!));
      const passengers = intParam(params, 'passengers', 1);
      const results: CarSearchResult[] = carOffers
        .filter(
          (c) =>
            c.cityId === params.get('cityId') &&
            (c.seats ?? 0) >= passengers &&
            matches(params, 'carCategory', c.carCategory),
        )
        .map((c) => ({
          offerId: `curated:car:${c.id}`,
          provider: 'curated',
          cityId: c.cityId,
          companyName: c.companyName,
          carModel: c.carModel,
          carCategory: c.carCategory,
          transmission: c.transmission,
          seats: c.seats ?? 0,
          days,
          pricePerDay: c.pricePerDay,
          totalPrice: round(c.pricePerDay * days),
          currency: c.currency,
        }))
        .filter((r) => inPriceRange(params, r.totalPrice));
      return HttpResponse.json(searchPage(results, params));
    }),

    http.get(
      '*/api/me',
      authenticated(async () => {
        await wait();
        return HttpResponse.json(me);
      }),
    ),

    http.post(
      '*/api/me/quotes',
      authenticated(async ({ request }) => {
        await wait();
        const body = (await request.json()) as QuoteRequest;
        if (!body.passengers || body.passengers < 1 || body.passengers > 9) {
          return problem(request, 400, 'Validation failed', 'Um ou mais campos sao invalidos.', {
            errors: { passengers: 'must be between 1 and 9' },
          });
        }
        if (body.passengers === 8) {
          return problem(
            request,
            422,
            'Unprocessable Entity',
            'Combinação não pode ser precificada.',
          );
        }
        const quote = buildQuote(body);
        if (!quote) {
          return problem(request, 404, 'Not Found', 'Pacote ou oferta não encontrado.');
        }
        state.quotes.set(quote.id, quote);
        state.save();
        state.save();
        return HttpResponse.json(quote, {
          status: 201,
          headers: { Location: `/api/me/quotes/${quote.id}` },
        });
      }),
    ),

    http.get(
      '*/api/me/quotes/:id',
      authenticated(async ({ params, request }) => {
        await wait();
        const quote = state.quotes.get(String(params['id']));
        return quote ? HttpResponse.json(withQuoteStatus(quote, state)) : notFound(request);
      }),
    ),

    // Fase 6 (docs/api/checkout.yaml): reserva e checkout hospedado
    http.post(
      '*/api/me/bookings',
      authenticated(async ({ request }) => {
        await wait();
        const { quoteId } = (await request.json()) as BookingRequest;
        const quote = state.quotes.get(quoteId);
        if (!quote) return notFound(request);
        const existing = [...state.bookings.values()].find((b) => b.quoteId === quoteId);
        if (existing) {
          return coded(request, 409, 'QUOTE_ALREADY_BOOKED', 'Esta cotacao ja virou reserva.', {
            bookingId: existing.id,
          });
        }
        if (withQuoteStatus(quote, state).status === 'EXPIRED') {
          return coded(request, 409, 'QUOTE_EXPIRED', 'A cotacao expirou. Gere uma nova cotacao.');
        }
        const booking: Booking = {
          id: crypto.randomUUID(),
          status: 'PENDING_PAYMENT',
          quoteId: quote.id,
          travelPackageId: quote.travelPackageId,
          passengers: quote.passengers,
          items: quote.items,
          currency: quote.currency,
          subtotal: quote.subtotal,
          discount: quote.discount,
          serviceFee: quote.serviceFee,
          total: quote.total,
          expiresAt: new Date(Date.now() + BOOKING_PAYMENT_WINDOW_MS).toISOString(),
          confirmedAt: null,
          cancelledAt: null,
          cancellationReason: null,
          payment: null,
          createdAt: new Date().toISOString(),
        };
        state.bookings.set(booking.id, booking);
        state.save();
        return HttpResponse.json(booking, {
          status: 201,
          headers: { Location: `/api/me/bookings/${booking.id}` },
        });
      }),
    ),

    http.get(
      '*/api/me/bookings/:id',
      authenticated(async ({ params, request }) => {
        await wait();
        const id = String(params['id']);
        const booking = state.bookings.get(id);
        if (!booking) return notFound(request);
        // Simula o webhook: a partir da 2ª consulta depois do checkout, o pagamento confirma.
        if (state.polls.has(id) && booking.status === 'PENDING_PAYMENT' && booking.payment) {
          const polls = state.polls.get(id)! + 1;
          state.polls.set(id, polls);
          if (polls >= 2) {
            const now = new Date().toISOString();
            booking.status = 'CONFIRMED';
            booking.confirmedAt = now;
            booking.payment = { ...booking.payment, status: 'SUCCEEDED', paidAt: now };
          }
          state.save();
        }
        return HttpResponse.json(booking);
      }),
    ),

    http.post(
      '*/api/me/bookings/:id/checkout-session',
      authenticated(async ({ params, request }) => {
        await wait();
        const id = String(params['id']);
        const booking = state.bookings.get(id);
        if (!booking) return notFound(request);
        if (booking.status !== 'PENDING_PAYMENT') {
          return coded(
            request,
            409,
            'BOOKING_NOT_PAYABLE',
            'A reserva nao esta aguardando pagamento.',
          );
        }
        if (Date.parse(booking.expiresAt) <= Date.now() && !booking.payment) {
          return coded(request, 409, 'BOOKING_EXPIRED', 'O prazo para pagar esta reserva acabou.');
        }
        const { paymentMethod } = (await request.json()) as CheckoutSessionRequest;
        const current = booking.payment;
        const reused = current?.status === 'PENDING' && current.method === paymentMethod;
        const payment = reused
          ? current
          : {
              id: crypto.randomUUID(),
              method: paymentMethod,
              status: 'PENDING' as const,
              installments: 1 as const,
              amount: booking.total,
              currency: booking.currency,
              refundedAmount: 0,
              paidAt: null,
            };
        booking.payment = payment;
        state.polls.set(id, 0);
        state.save();
        // No mock não há Stripe: a "página de pagamento" volta direto para a tela de sucesso.
        const session: CheckoutSession = {
          paymentId: payment.id,
          paymentMethod,
          checkoutUrl: `${location.origin}/checkout/${id}/sucesso`,
          expiresAt: new Date(Date.now() + BOOKING_PAYMENT_WINDOW_MS).toISOString(),
        };
        return HttpResponse.json(session, { status: reused ? 200 : 201 });
      }),
    ),
  ];
}

function authenticated<T extends Parameters<HttpResponseResolver>[0]>(
  resolver: (info: T) => Promise<Response>,
) {
  return (info: T) => {
    const auth = info.request.headers.get('Authorization') ?? '';
    if (!auth.startsWith(`Bearer ${MOCK_TOKEN_PREFIX}`)) {
      return problem(info.request, 401, 'Unauthorized', 'Authentication is required.');
    }
    return resolver(info);
  };
}

function buildQuote(body: QuoteRequest): Quote | null {
  const items: Quote['items'] = [];
  let subtotal = 0;
  let discount = 0;
  const pax = body.passengers;

  if (body.travelPackageId) {
    const pkg = travelPackages.find((p) => p.id === body.travelPackageId);
    if (!pkg) return null;
    const flight = flights.find((f) => f.id === pkg.flightId)!;
    const start = flight.boardingDate.slice(0, 10);
    const end = flight.returnDate?.slice(0, 10) ?? start;
    items.push({
      type: 'FLIGHT',
      description: `${flight.companyName} - ${flight.boardingAirport} / ${flight.returnAirport}`,
      startDate: start,
      endDate: end,
      quantity: pax,
    });
    for (const c of pkg.components) {
      if (c.type === 'HOTEL_STAY') {
        const room = roomOffers.find((r) => r.id === c.roomOfferId)!;
        const hotel = hotels.find((h) => h.id === room.hotelId)!;
        items.push({
          type: 'HOTEL_STAY',
          description: `${hotel.name}, ${room.roomType}`,
          startDate: start,
          endDate: end,
          quantity: c.quantity ?? 1,
        });
      } else {
        const car = carOffers.find((o) => o.id === c.carRentalOfferId)!;
        items.push({
          type: 'CAR_RENTAL',
          description: `${car.carModel} (${car.companyName})`,
          startDate: start,
          endDate: end,
          quantity: c.quantity ?? 1,
        });
      }
    }
    subtotal = pkg.price * pax;
    discount = pkg.pricePromotion != null ? (pkg.price - pkg.pricePromotion) * pax : 0;
  } else if (body.flight || body.hotel || body.car) {
    if (body.flight) {
      const flight = flights.find((f) => body.flight!.offerId.endsWith(f.id));
      if (!flight) return null;
      items.push({
        type: 'FLIGHT',
        description: flight.companyName,
        startDate: flight.boardingDate.slice(0, 10),
        endDate: (flight.returnDate ?? flight.boardingDate).slice(0, 10),
        quantity: pax,
      });
      subtotal += flight.pricePerPassenger * pax;
    }
    if (body.hotel) {
      const room = roomOffers.find((r) => body.hotel!.offerId.endsWith(r.id));
      if (!room) return null;
      const nights = daysBetween(body.hotel.checkIn, body.hotel.checkOut);
      items.push({
        type: 'HOTEL_STAY',
        description: room.roomType,
        startDate: body.hotel.checkIn,
        endDate: body.hotel.checkOut,
        quantity: 1,
      });
      subtotal += room.pricePerNight * nights;
    }
    if (body.car) {
      const car = carOffers.find((o) => body.car!.offerId.endsWith(o.id));
      if (!car) return null;
      const days = Math.max(1, daysBetween(body.car.pickUpDate, body.car.dropOffDate));
      items.push({
        type: 'CAR_RENTAL',
        description: car.carModel,
        startDate: body.car.pickUpDate,
        endDate: body.car.dropOffDate,
        quantity: 1,
      });
      subtotal += car.pricePerDay * days;
    }
  } else {
    return null;
  }

  const serviceFee = round((subtotal - discount) * SERVICE_FEE_RATE);
  const now = Date.now();
  return {
    id: crypto.randomUUID(),
    status: 'OPEN',
    travelPackageId: body.travelPackageId ?? null,
    passengers: pax,
    items,
    currency: 'BRL',
    subtotal: round(subtotal),
    discount: round(discount),
    serviceFee,
    total: round(subtotal - discount + serviceFee),
    expiresAt: new Date(now + QUOTE_VALIDITY_MS).toISOString(),
    createdAt: new Date(now).toISOString(),
  };
}

function withQuoteStatus(quote: Quote, state: MockState): Quote {
  if ([...state.bookings.values()].some((b) => b.quoteId === quote.id)) {
    return { ...quote, status: 'BOOKED' };
  }
  return Date.parse(quote.expiresAt) <= Date.now() ? { ...quote, status: 'EXPIRED' } : quote;
}

function coded(
  request: Request,
  status: number,
  code: ProblemCode,
  detail: string,
  extra: Record<string, unknown> = {},
) {
  const title = status === 409 ? 'Conflict' : 'Unprocessable Entity';
  return problem(request, status, title, detail, { code, ...extra });
}

function problem(
  request: Request,
  status: number,
  title: string,
  detail: string,
  extra: Record<string, unknown> = {},
) {
  const body: ProblemDetail & Record<string, unknown> = {
    type: 'about:blank',
    title,
    status,
    detail,
    instance: new URL(request.url).pathname,
    ...extra,
  };
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function notFound(request: Request) {
  return problem(request, 404, 'Not Found', 'Recurso não encontrado.');
}

function findOr404<T extends { id: string }>(items: T[], id: string, request: Request) {
  const item = items.find((i) => i.id === id);
  return item ? HttpResponse.json(item) : notFound(request);
}

function requireParams(request: Request, params: URLSearchParams, names: string[]) {
  const missing = names.filter((n) => !params.get(n));
  if (missing.length === 0) return null;
  return problem(request, 400, 'Validation failed', 'Um ou mais campos sao invalidos.', {
    errors: Object.fromEntries(missing.map((n) => [n, 'must not be null'])),
  });
}

function paginate<T>(items: T[], params: URLSearchParams) {
  const page = intParam(params, 'page', 0);
  const size = Math.min(intParam(params, 'size', 20), 100);
  return {
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

function searchPage<T extends { totalPrice: number }>(
  items: T[],
  params: URLSearchParams,
): SearchPage<T> {
  const desc = (params.get('sort') ?? '').endsWith(',desc');
  const sorted = [...items].sort((a, b) => (desc ? -1 : 1) * (a.totalPrice - b.totalPrice));
  return { ...paginate(sorted, params), unavailableProviders: [] };
}

function intParam(params: URLSearchParams, name: string, fallback: number): number {
  const value = Number.parseInt(params.get(name) ?? '', 10);
  return Number.isNaN(value) ? fallback : value;
}

function matches(params: URLSearchParams, name: string, value: string | null | undefined) {
  const wanted = params.get(name);
  return !wanted || wanted === value;
}

function dateFrom(params: URLSearchParams, name: string, value: string) {
  const from = params.get(name);
  return !from || value.slice(0, 10) >= from;
}

function dateTo(params: URLSearchParams, name: string, value: string) {
  const to = params.get(name);
  return !to || value.slice(0, 10) <= to;
}

function inPriceRange(params: URLSearchParams, value: number) {
  const min = Number(params.get('minPrice') ?? Number.NEGATIVE_INFINITY);
  const max = Number(params.get('maxPrice') ?? Number.POSITIVE_INFINITY);
  return value >= min && value <= max;
}

function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
