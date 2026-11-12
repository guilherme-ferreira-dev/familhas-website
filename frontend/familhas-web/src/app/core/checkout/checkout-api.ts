import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  Booking,
  CheckoutSession,
  PaymentMethod,
  Quote,
  QuoteRequest,
} from '../api/api-types';
import { APP_CONFIG } from '../config/app-config';

/** Cotação, reserva e checkout do cliente logado (docs/api/checkout.yaml, Fase 6). */
@Injectable({ providedIn: 'root' })
export class CheckoutApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/api/me`;

  createQuote(request: QuoteRequest): Observable<Quote> {
    return this.http.post<Quote>(`${this.base}/quotes`, request);
  }

  quote(id: string): Observable<Quote> {
    return this.http.get<Quote>(`${this.base}/quotes/${encodeURIComponent(id)}`);
  }

  createBooking(quoteId: string): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings`, { quoteId });
  }

  booking(id: string): Observable<Booking> {
    return this.http.get<Booking>(`${this.base}/bookings/${encodeURIComponent(id)}`);
  }

  createCheckoutSession(
    bookingId: string,
    paymentMethod: PaymentMethod,
  ): Observable<CheckoutSession> {
    return this.http.post<CheckoutSession>(
      `${this.base}/bookings/${encodeURIComponent(bookingId)}/checkout-session`,
      { paymentMethod },
    );
  }
}
