import { API, page, render, settle } from '../../../testing/setup';
import { NewQuotePage } from './new-quote.page';

const ana = {
  id: 'c1',
  name: 'Ana',
  email: 'ana@x.com',
  status: 'ACTIVE',
  hasLogin: true,
  favoriteIds: [],
  myFlightIds: [],
};

describe('NewQuotePage', () => {
  async function open() {
    const { fixture, http } = await render(NewQuotePage, { cliente: 'c1' });
    http.expectOne(`${API}/clients/c1`).flush(ana);
    http.expectOne((r) => r.url === `${API}/travel-packages`).flush(page([]));
    await settle(fixture);
    return { fixture, http, component: fixture.componentInstance };
  }

  it('pede o pacote quando monta por pacote', async () => {
    const { component } = await open();
    expect(component.buildRequest()).toBe('Escolha o pacote.');

    component['form'].patchValue({
      travelPackageId: 'p1',
      manualDiscount: 150.255,
      internalNote: '  WhatsApp ',
    });
    expect(component.buildRequest()).toEqual({
      clientId: 'c1',
      passengers: 2,
      travelPackageId: 'p1',
      manualDiscount: 150.26,
      validityHours: 24,
      internalNote: 'WhatsApp',
    });
  });

  it('itens avulsos viram ofertas do catálogo curado e exigem datas coerentes', async () => {
    const { component } = await open();
    component['form'].patchValue({ mode: 'items' });
    expect(component.buildRequest()).toBe('Escolha ao menos um item.');

    component['form'].patchValue({
      flightId: 'f1',
      roomOfferId: 'r1',
      checkIn: '2027-03-10',
      checkOut: '2027-03-10',
    });
    expect(component.buildRequest()).toContain('check-out');

    component['form'].patchValue({ checkOut: '2027-03-17' });
    expect(component.buildRequest()).toMatchObject({
      flight: { offerId: 'curated:f1' },
      hotel: { offerId: 'curated:r1', checkIn: '2027-03-10', checkOut: '2027-03-17' },
      car: null,
    });
  });

  it('mostra o desconto máximo quando fica abaixo do custo', async () => {
    const { fixture, http, component } = await open();
    component['form'].patchValue({ travelPackageId: 'p1', manualDiscount: 9000 });

    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    http
      .expectOne(`${API}/quotes`)
      .flush(
        { status: 422, code: 'QUOTE_BELOW_COST', maxManualDiscount: 482.95 },
        { status: 422, statusText: 'x' },
      );
    await settle(fixture);

    expect((fixture.nativeElement.textContent as string).replace(/\s+/g, ' ')).toContain(
      'Desconto máximo para esta cotação: R$ 482,95',
    );
  });
});
