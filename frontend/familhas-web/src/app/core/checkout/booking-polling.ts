import { concat, Observable, switchMap, takeWhile, timer } from 'rxjs';
import type { Booking } from '../api/api-types';

/** Ritmo sugerido no checkout.yaml: a cada 3 s por até 2 min, depois a cada 15 s. */
export const FAST_INTERVAL_MS = 3_000;
export const FAST_WINDOW_MS = 120_000;
export const SLOW_INTERVAL_MS = 15_000;

/**
 * Consulta a reserva até ela sair de PENDING_PAYMENT. A volta do Stripe não confirma nada:
 * quem confirma é o webhook (e o Pix é assíncrono). Emite cada leitura, inclusive a final.
 */
export function pollBooking(load: () => Observable<Booking>): Observable<Booking> {
  const fastTicks = Math.floor(FAST_WINDOW_MS / FAST_INTERVAL_MS);
  const fast = timer(0, FAST_INTERVAL_MS).pipe(takeWhile((i) => i < fastTicks));
  const slow = timer(SLOW_INTERVAL_MS, SLOW_INTERVAL_MS);
  // switchMap descarta uma consulta lenta quando chega a próxima: nunca empilha requisições.
  return concat(fast, slow).pipe(
    switchMap(() => load()),
    takeWhile((booking) => booking.status === 'PENDING_PAYMENT', true),
  );
}
