import { API, render, settle } from '../../../testing/setup';
import { BookingDetailPage } from './booking-detail.page';
import { BookingsPage } from './bookings.page';

describe('BookingsPage', () => {
  it('manda os filtros da URL para a API', async () => {
    const { http } = await render(BookingsPage, {
      status: 'CONFIRMED',
      de: '2027-01-01',
      ate: '2027-01-31',
    });

    const req = http.expectOne((r) => r.url === `${API}/bookings`);
    expect(req.request.params.get('status')).toBe('CONFIRMED');
    expect(req.request.params.get('from')).toBe('2027-01-01');
    expect(req.request.params.get('to')).toBe('2027-01-31');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
  });
});

describe('BookingDetailPage', () => {
  it('mostra custo, margem e as tentativas de pagamento', async () => {
    const { fixture, http } = await render(BookingDetailPage, { id: 'b1' });

    http.expectOne(`${API}/bookings/b1`).flush({
      id: 'b1000000-0000',
      status: 'CANCELLED',
      cancellationReason: 'PAYMENT_EXPIRED',
      client: { id: 'c1', name: 'Ana', email: 'ana@x.com' },
      travelPackageTitle: 'Maceió',
      passengers: 2,
      currency: 'BRL',
      totalAmount: 1100,
      componentsTotal: 1000,
      marginPercent: 10,
      marginAmount: 100,
      promotionPercent: 0,
      discountAmount: 0,
      serviceFeeAmount: 0,
      expiresAt: '2027-01-01T12:00:00Z',
      createdAt: '2027-01-01T11:30:00Z',
      quoteId: 'q1',
      items: [
        {
          type: 'FLIGHT',
          description: 'GOL',
          startDate: '2027-03-10',
          endDate: '2027-03-17',
          quantity: 2,
          unitPrice: 500,
          currency: 'BRL',
          subtotal: 1000,
          provider: 'curated',
        },
      ],
      payments: [],
    });
    await settle(fixture);

    const text = (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
    expect(text).toContain('prazo de pagamento vencido');
    expect(text).toContain('Margem (10%)');
    expect(text).toContain('R$ 1.100,00');
    expect(text).toContain('Nenhum pagamento.');
  });
});
