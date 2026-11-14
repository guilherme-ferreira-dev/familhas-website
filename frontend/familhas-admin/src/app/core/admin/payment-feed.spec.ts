import { TestBed } from '@angular/core/testing';
import type { PaymentStreamEvent } from '../api/api-types';
import { AuthService } from '../auth/auth.service';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { PaymentFeed } from './payment-feed';

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

describe('PaymentFeed', () => {
  let feed: PaymentFeed;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PaymentFeed,
        { provide: APP_CONFIG, useValue: { apiBaseUrl: 'http://api' } as AppConfig },
        { provide: AuthService, useValue: { getAccessToken: () => Promise.resolve('tok') } },
      ],
    });
    feed = TestBed.inject(PaymentFeed);
  });

  afterEach(() => feed.stop());

  it('manda o token no header (nunca na URL) e repassa os avisos de pagamento', async () => {
    const received: PaymentStreamEvent[] = [];
    feed.events.subscribe((e) => received.push(e));
    const calls: [string, RequestInit][] = [];
    const fetchFn = vi.fn(async (url: string, init: RequestInit) => {
      calls.push([url, init]);
      if (calls.length > 1) return new Promise<Response>(() => undefined); // segunda conexão fica aberta
      return new Response(
        streamOf([
          'event:ready\ndata:{}\n\n',
          'event:payment\ndata:{"eventType":"PaymentConfirmed","paymentId":"p1",',
          '"bookingId":"b1","occurredAt":"2027-01-10T15:00:00Z"}\n\n',
        ]),
        { status: 200 },
      );
    });

    feed.start(fetchFn as unknown as typeof fetch);
    await vi.waitFor(() => expect(calls.length).toBe(2));

    expect(calls[0][0]).toBe('http://api/api/admin/payments/stream');
    expect((calls[0][1].headers as Record<string, string>)['Authorization']).toBe('Bearer tok');
    expect(received.map((e) => e.paymentId)).toEqual(['p1']);
  });

  it('para de tentar quando a API responde 403', async () => {
    const fetchFn = vi.fn(async () => new Response(null, { status: 403 }));

    feed.start(fetchFn as unknown as typeof fetch);
    await vi.waitFor(() => expect(feed.status()).toBe('offline'));
    await new Promise((r) => setTimeout(r, 20));

    expect(fetchFn).toHaveBeenCalledTimes(1);
  });
});
