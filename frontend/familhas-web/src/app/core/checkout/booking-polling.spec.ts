import { of } from 'rxjs';
import type { Booking } from '../api/api-types';
import { FAST_INTERVAL_MS, FAST_WINDOW_MS, pollBooking, SLOW_INTERVAL_MS } from './booking-polling';

const booking = (status: Booking['status']) => ({ id: 'b1', status }) as Booking;

describe('pollBooking', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('consulta a cada 3 s e para quando a reserva sai de PENDING_PAYMENT', () => {
    const statuses: Booking['status'][] = [
      'PENDING_PAYMENT',
      'PENDING_PAYMENT',
      'CONFIRMED',
      'CONFIRMED',
    ];
    let calls = 0;
    const seen: string[] = [];
    let done = false;
    pollBooking(() => of(booking(statuses[calls++]))).subscribe({
      next: (b) => seen.push(b.status),
      complete: () => (done = true),
    });
    vi.advanceTimersByTime(0);
    vi.advanceTimersByTime(FAST_INTERVAL_MS * 2);
    expect(seen).toEqual(['PENDING_PAYMENT', 'PENDING_PAYMENT', 'CONFIRMED']);
    expect(done).toBe(true);
    vi.advanceTimersByTime(FAST_INTERVAL_MS * 5);
    expect(calls).toBe(3);
  });

  it('depois de 2 min passa a consultar a cada 15 s', () => {
    let calls = 0;
    pollBooking(() => {
      calls++;
      return of(booking('PENDING_PAYMENT'));
    }).subscribe();
    vi.advanceTimersByTime(FAST_WINDOW_MS - 1);
    const fastCalls = calls;
    expect(fastCalls).toBe(FAST_WINDOW_MS / FAST_INTERVAL_MS);
    vi.advanceTimersByTime(SLOW_INTERVAL_MS + 1);
    expect(calls).toBe(fastCalls + 1);
    vi.advanceTimersByTime(SLOW_INTERVAL_MS);
    expect(calls).toBe(fastCalls + 2);
  });
});
