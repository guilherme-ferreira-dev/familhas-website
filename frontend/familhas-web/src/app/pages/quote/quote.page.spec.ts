import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { http, HttpResponse } from 'msw';
import type { Quote } from '../../core/api/api-types';
import { useMockApi } from '../../../testing/mock-api';
import { provideTestConfig } from '../../../testing/test-config';
import { WhatsappContextService } from '../../core/whatsapp/whatsapp';
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
