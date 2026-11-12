import { setupServer } from 'msw/node';
import type { Booking, CheckoutSession, PackageSearchPage, Quote } from '../app/core/api/api-types';
import { createHandlers, createMockState } from './handlers';
import { CITY, travelPackages } from './fixtures';

const API = 'http://api.test';
const AUTH = { Authorization: 'Bearer mock-token', 'Content-Type': 'application/json' };
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
beforeEach(() => server.resetHandlers(...createHandlers(createMockState(), 0)));
afterAll(() => server.close());

async function post<T>(path: string, body: unknown): Promise<{ status: number; body: T }> {
  const res = await fetch(`${API}${path}`, {
    method: 'POST',
    headers: AUTH,
    body: JSON.stringify(body),
  });
  return { status: res.status, body: (await res.json()) as T };
}

async function get<T>(path: string): Promise<T> {
  return (await (await fetch(`${API}${path}`, { headers: AUTH })).json()) as T;
}

describe('mocks da API', () => {
  it('filtra pacotes por destino e calcula o total pelo número de passageiros', async () => {
    const page = await get<PackageSearchPage>(
      `/api/search/packages?destinationCityId=${CITY.maceio}&passengers=2`,
    );
    expect(page.content).toHaveLength(1);
    expect(page.content[0].totalPrice).toBe(page.content[0].pricePerPerson * 2);
    expect(page.unavailableProviders).toEqual([]);
  });

  it('responde 400 em Problem Details quando falta filtro obrigatório', async () => {
    const res = await fetch(`${API}/api/search/flights?originCityId=${CITY.brasilia}`);
    expect(res.status).toBe(400);
    expect(res.headers.get('Content-Type')).toContain('application/problem+json');
    expect((await res.json()).errors).toHaveProperty('destinationCityId');
  });

  it('exige token nas rotas /api/me', async () => {
    const res = await fetch(`${API}/api/me`);
    expect(res.status).toBe(401);
  });

  it('percorre cotação, reserva, checkout e confirmação por polling', async () => {
    const quote = await post<Quote>('/api/me/quotes', {
      travelPackageId: travelPackages[0].id,
      passengers: 2,
    });
    expect(quote.status).toBe(201);
    expect(quote.body.total).toBe(
      quote.body.subtotal - quote.body.discount + quote.body.serviceFee,
    );

    const booking = await post<Booking>('/api/me/bookings', { quoteId: quote.body.id });
    expect(booking.status).toBe(201);
    expect(booking.body.status).toBe('PENDING_PAYMENT');

    const again = await post<{ code: string; bookingId: string }>('/api/me/bookings', {
      quoteId: quote.body.id,
    });
    expect(again.status).toBe(409);
    expect(again.body).toMatchObject({ code: 'QUOTE_ALREADY_BOOKED', bookingId: booking.body.id });
    expect((await get<Quote>(`/api/me/quotes/${quote.body.id}`)).status).toBe('BOOKED');

    const path = `/api/me/bookings/${booking.body.id}/checkout-session`;
    const session = await post<CheckoutSession>(path, { paymentMethod: 'PIX' });
    expect(session.status).toBe(201);
    expect(session.body.checkoutUrl).toContain(`/checkout/${booking.body.id}/sucesso`);
    expect((await post<CheckoutSession>(path, { paymentMethod: 'PIX' })).status).toBe(200);

    const first = await get<Booking>(`/api/me/bookings/${booking.body.id}`);
    expect(first.status).toBe('PENDING_PAYMENT');
    const second = await get<Booking>(`/api/me/bookings/${booking.body.id}`);
    expect(second.status).toBe('CONFIRMED');
    expect(second.payment?.status).toBe('SUCCEEDED');

    const paid = await post<{ code: string }>(path, { paymentMethod: 'PIX' });
    expect(paid).toMatchObject({ status: 409, body: { code: 'BOOKING_NOT_PAYABLE' } });
  });
});
