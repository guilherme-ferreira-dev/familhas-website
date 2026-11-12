import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { http, HttpResponse } from 'msw';
import type { Booking } from '../../core/api/api-types';
import { useMockApi } from '../../../testing/mock-api';
import { provideTestConfig } from '../../../testing/test-config';
import { AuthService } from '../../core/auth/auth.service';
import { jwtInterceptor } from '../../core/auth/jwt.interceptor';
import { PaymentRedirect } from '../../core/checkout/payment-redirect';
import { errorInterceptor } from '../../core/errors/error.interceptor';
import { CheckoutReturnPage } from './checkout-return.page';
import { CheckoutPage } from './checkout.page';

const pending: Booking = {
  id: '8d0e4c7a-31b2-4f0f-9c64-5b0a2e7d4f10',
  status: 'PENDING_PAYMENT',
  quoteId: 'q1',
  travelPackageId: 'p1',
  passengers: 2,
  items: [
    {
      type: 'FLIGHT',
      description: 'Azul BSB → MCZ',
      startDate: '2027-01-10',
      endDate: '2027-01-17',
      quantity: 2,
    },
  ],
  currency: 'BRL',
  subtotal: 8580,
  discount: 800,
  serviceFee: 389,
  total: 8169,
  expiresAt: '2027-01-01T15:30:00Z',
  confirmedAt: null,
  cancelledAt: null,
  cancellationReason: null,
  payment: null,
  createdAt: '2027-01-01T15:00:00Z',
};

describe('Checkout', () => {
  const server = useMockApi();
  const go = vi.fn();
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    go.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'checkout/:id', component: CheckoutPage },
            {
              path: 'checkout/:id/sucesso',
              component: CheckoutReturnPage,
              data: { outcome: 'sucesso' },
            },
            {
              path: 'checkout/:id/cancelado',
              component: CheckoutReturnPage,
              data: { outcome: 'cancelado' },
            },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideTestConfig(),
        { provide: PaymentRedirect, useValue: { go } },
        {
          provide: AuthService,
          useValue: { login: vi.fn(), getAccessToken: () => Promise.resolve('mock-token') },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  async function open(url: string, ready: (el: HTMLElement) => boolean) {
    await harness.navigateByUrl(url);
    const el = () => harness.routeNativeElement as HTMLElement;
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(ready(el())).toBe(true);
    });
    return el();
  }

  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ');

  it('escolhe Pix ou cartão e vai para o Stripe com a URL devolvida pela API', async () => {
    let sent: unknown;
    server.use(
      http.get('*/api/me/bookings/:id', () => HttpResponse.json(pending)),
      http.post('*/api/me/bookings/:id/checkout-session', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(
          {
            paymentId: 'pay1',
            paymentMethod: 'CREDIT_CARD',
            checkoutUrl: 'https://checkout.stripe.com/c/pay/cs_test_1',
            expiresAt: '2027-01-01T15:30:00Z',
          },
          { status: 201 },
        );
      }),
    );
    const el = await open(`/checkout/${pending.id}`, (e) => !!e.querySelector('form'));
    expect(text(el)).toContain('8D0E4C7A');
    expect(text(el)).toContain('Seus lugares ficam guardados até');
    const card = el.querySelector<HTMLInputElement>('#metodo-CREDIT_CARD')!;
    card.click();
    harness.detectChanges();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await vi.waitFor(() =>
      expect(go).toHaveBeenCalledWith('https://checkout.stripe.com/c/pay/cs_test_1'),
    );
    expect(sent).toEqual({ paymentMethod: 'CREDIT_CARD' });
  });

  it('Pix já em processamento leva para a tela de confirmação', async () => {
    server.use(
      http.get('*/api/me/bookings/:id', () => HttpResponse.json(pending)),
      http.post('*/api/me/bookings/:id/checkout-session', () =>
        HttpResponse.json({ status: 409, code: 'PAYMENT_IN_PROGRESS' }, { status: 409 }),
      ),
    );
    const el = await open(`/checkout/${pending.id}`, (e) => !!e.querySelector('form'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await vi.waitFor(() =>
      expect(TestBed.inject(Router).url).toBe(`/checkout/${pending.id}/sucesso`),
    );
  });

  it('reserva vencida mostra o motivo pelo code e não abre o Stripe', async () => {
    server.use(
      http.get('*/api/me/bookings/:id', () => HttpResponse.json(pending)),
      http.post('*/api/me/bookings/:id/checkout-session', () =>
        HttpResponse.json(
          { status: 409, code: 'BOOKING_EXPIRED', detail: 'texto técnico' },
          { status: 409 },
        ),
      ),
    );
    const el = await open(`/checkout/${pending.id}`, (e) => !!e.querySelector('form'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain('prazo para pagar');
    });
    expect(text(el)).not.toContain('texto técnico');
    expect(go).not.toHaveBeenCalled();
  });

  it('na volta do Stripe, consulta até o webhook confirmar', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/me/bookings/:id', () =>
        HttpResponse.json(++calls < 2 ? pending : { ...pending, status: 'CONFIRMED' }),
      ),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const el = await open(`/checkout/${pending.id}/sucesso`, (e) =>
        text(e).includes('aguardando'),
      );
      await vi.advanceTimersByTimeAsync(3_000);
      await vi.waitFor(() => {
        harness.detectChanges();
        expect(text(el)).toContain('Reserva confirmada!');
      });
      expect(text(el)).toMatch(/Pagamento de R\$\s8\.169,00 confirmado/);
    } finally {
      vi.useRealTimers();
    }
  });

  it('cancelado no Stripe oferece tentar de novo sem consultar nada', async () => {
    const el = await open(`/checkout/${pending.id}/cancelado`, (e) => !!e.querySelector('h1'));
    expect(text(el)).toContain('Nada foi cobrado');
    expect(el.querySelector(`a[href="/checkout/${pending.id}"]`)).not.toBeNull();
  });
});
