import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { http, HttpResponse } from 'msw';
import type { Quote } from '../../core/api/api-types';
import { useMockApi } from '../../../testing/mock-api';
import { provideTestConfig } from '../../../testing/test-config';
import { WhatsappContextService } from '../../core/whatsapp/whatsapp';
import { AuthService } from '../../core/auth/auth.service';
import { errorInterceptor } from '../../core/errors/error.interceptor';
import { QuotePage } from './quote.page';

const quote: Quote = {
  id: 'abcdef12-0000-4000-8000-000000000001',
  status: 'OPEN',
  travelPackageId: 'p1',
  passengers: 2,
  items: [
    {
      type: 'FLIGHT',
      description: 'GOL - BSB / MCZ',
      startDate: '2027-01-10',
      endDate: '2027-01-17',
      quantity: 2,
    },
    {
      type: 'HOTEL_STAY',
      description: 'Resort',
      startDate: '2027-01-10',
      endDate: '2027-01-17',
      quantity: 1,
    },
  ],
  currency: 'BRL',
  subtotal: 8580,
  discount: 800,
  serviceFee: 389,
  total: 8169,
  expiresAt: '2027-01-01T15:00:00Z',
  createdAt: '2026-12-31T15:00:00Z',
};

describe('QuotePage', () => {
  const server = useMockApi();

  async function open(body: Quote) {
    server.use(http.get('*/api/me/quotes/:id', () => HttpResponse.json(body)));
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'cotacoes/:id', component: QuotePage }],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideTestConfig(),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/cotacoes/${body.id}`);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(harness.routeNativeElement?.querySelector('dl')).not.toBeNull();
    });
    return harness.routeNativeElement as HTMLElement;
  }

  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ');

  it('mostra itens, desconto, taxa, total e validade', async () => {
    const el = await open(quote);
    expect(text(el)).toContain('ABCDEF12');
    expect(text(el)).toMatch(/Voo\s*GOL - BSB \/ MCZ/);
    expect(text(el)).toMatch(/Desconto\s*−\s*R\$\s800,00/);
    expect(text(el)).toMatch(/Total\s*R\$\s8\.169,00/);
    expect(text(el)).toContain('Válida até');
    expect(TestBed.inject(WhatsappContextService).context()).toMatchObject({
      kind: 'quote',
      total: 8169,
    });
  });

  it('avisa quando a cotação expirou', async () => {
    const el = await open({ ...quote, status: 'EXPIRED' });
    expect(el.querySelector('[role="status"]')?.textContent).toContain('expirou');
  });
});

describe('QuotePage: reservar', () => {
  const server = useMockApi();

  it('cotação já reservada leva para a reserva existente (409 QUOTE_ALREADY_BOOKED)', async () => {
    server.use(
      http.get('*/api/me/quotes/:id', () => HttpResponse.json({ ...quote, status: 'BOOKED' })),
      http.post('*/api/me/bookings', () =>
        HttpResponse.json(
          { status: 409, code: 'QUOTE_ALREADY_BOOKED', bookingId: 'b-existente' },
          { status: 409 },
        ),
      ),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'cotacoes/:id', component: QuotePage },
            { path: 'checkout/:id', children: [] },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideTestConfig(),
        { provide: AuthService, useValue: { login: vi.fn() } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/cotacoes/${quote.id}`);
    let button: HTMLButtonElement | undefined;
    await vi.waitFor(() => {
      harness.detectChanges();
      button = [...harness.routeNativeElement!.querySelectorAll('button')].find((b) =>
        b.textContent?.includes('Ver minha reserva'),
      );
      expect(button).toBeDefined();
    });
    button!.click();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/checkout/b-existente'));
  });
});
