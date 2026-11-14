import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import type { PaymentStreamEvent } from '../../core/api/api-types';
import { PaymentFeed } from '../../core/admin/payment-feed';
import { API, page, render, settle } from '../../../testing/setup';
import { PaymentsPage } from './payments.page';

describe('PaymentsPage', () => {
  const events = new Subject<PaymentStreamEvent>();

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    TestBed.overrideComponent(PaymentsPage, {
      set: {
        providers: [
          {
            provide: PaymentFeed,
            useValue: { start: vi.fn(), status: () => 'live', events: events.asObservable() },
          },
        ],
      },
    });
  });

  afterEach(() => vi.useRealTimers());

  it('manda período, vários status, método e parcelas para a API', async () => {
    const { http } = await render(PaymentsPage, {
      de: '2027-01-01',
      ate: '2027-01-31',
      data: 'PAID',
      status: ['SUCCEEDED', 'REFUNDED', 'INVALIDO'],
      metodo: 'PIX',
      parcelas: '1',
    });

    const req = http.expectOne((r) => r.url === `${API}/payments`);
    const p = req.request.params;
    expect(p.get('from')).toBe('2027-01-01');
    expect(p.get('dateField')).toBe('PAID');
    expect(p.getAll('status')).toEqual(['SUCCEEDED', 'REFUNDED']);
    expect(p.get('method')).toBe('PIX');
    expect(p.get('installments')).toBe('1');
  });

  it('recarrega quando chega um evento de pagamento e a cada 30 s', async () => {
    const { fixture, http } = await render(PaymentsPage);
    http.expectOne((r) => r.url === `${API}/payments`).flush(page([]));
    await settle(fixture);

    events.next({
      eventType: 'PaymentConfirmed',
      paymentId: 'p1',
      bookingId: 'b1',
      occurredAt: '2027-01-01T00:00:00Z',
    });
    await vi.advanceTimersByTimeAsync(600);
    TestBed.tick();
    http.expectOne((r) => r.url === `${API}/payments`).flush(page([]));
    await settle(fixture);
    expect(fixture.nativeElement.textContent).toContain('Pagamento p1 atualizado.');

    await vi.advanceTimersByTimeAsync(30_000);
    TestBed.tick();
    http.expectOne((r) => r.url === `${API}/payments`);
  });

  it('exporta com os mesmos filtros', async () => {
    const { fixture, http } = await render(PaymentsPage, { metodo: 'CREDIT_CARD' });
    http.expectOne((r) => r.url === `${API}/payments`).flush(page([]));
    await settle(fixture);
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();

    const xlsx = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('XLSX'),
    ) as HTMLButtonElement;
    xlsx.click();

    const req = http.expectOne((r) => r.url === `${API}/payments/export`);
    expect(req.request.params.get('format')).toBe('xlsx');
    expect(req.request.params.get('method')).toBe('CREDIT_CARD');
    expect(req.request.responseType).toBe('blob');
  });
});
