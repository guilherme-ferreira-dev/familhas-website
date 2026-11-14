import { API, page, render, settle } from '../../../testing/setup';
import { CatalogFormPage } from './catalog-form.page';
import { FLIGHTS, PACKAGES, withSeconds } from './catalog-definitions';
import { PricingPage } from './pricing.page';
import { PromotionsPage } from './promotions.page';

describe('definições do catálogo', () => {
  it('voo: horário local com segundos, moeda em maiúsculas e números', () => {
    expect(withSeconds('2027-03-10T08:00')).toBe('2027-03-10T08:00:00');
    expect(
      FLIGHTS.toRequest({
        companyName: ' GOL ',
        boardingAirport: 'BSB',
        returnAirport: 'MCZ',
        boardingDate: '2027-03-10T08:00',
        returnDate: '2027-03-17T18:00',
        originCityId: 'o1',
        destinationCityId: 'd1',
        pricePerPassenger: '899.90',
        currency: 'brl',
        seatsAvailable: '120',
        status: 'ACTIVE',
      }),
    ).toMatchObject({
      companyName: 'GOL',
      boardingDate: '2027-03-10T08:00:00',
      currency: 'BRL',
      pricePerPassenger: 899.9,
      seatsAvailable: 120,
    });
  });

  it('pacote: componentes viram quarto ou carro e promoção vazia vira null', () => {
    const request = PACKAGES.toRequest({
      title: 'Maceió',
      price: 9000,
      pricePromotion: '',
      components: [
        { type: 'HOTEL_STAY', offerId: 'r1', quantity: 7 },
        { type: 'CAR_RENTAL', offerId: 'c1', quantity: '3' },
      ],
    }) as { pricePromotion: unknown; components: unknown[] };
    expect(request.pricePromotion).toBeNull();
    expect(request.components).toEqual([
      { type: 'HOTEL_STAY', roomOfferId: 'r1', carRentalOfferId: null, quantity: 7 },
      { type: 'CAR_RENTAL', roomOfferId: null, carRentalOfferId: 'c1', quantity: 3 },
    ]);
  });
});

describe('CatalogFormPage', () => {
  it('não envia formulário incompleto e cria o voo com os campos certos', async () => {
    const { fixture, http } = await render(CatalogFormPage, { tipo: 'voos' });
    http
      .expectOne((r) => r.url === `${API}/cities`)
      .flush(
        page([
          { id: 'o1', name: 'Brasília', country: 'Brasil', cityType: 'ORIGIN', status: 'ACTIVE' },
        ]),
      );
    await settle(fixture);

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await settle(fixture);
    expect(fixture.nativeElement.textContent).toContain('Revise os campos marcados.');
    http.expectNone(`${API}/flights`);

    const component = fixture.componentInstance as unknown as {
      form: () => { patchValue(v: object): void };
    };
    component.form().patchValue({
      companyName: 'GOL',
      boardingAirport: 'BSB',
      returnAirport: 'MCZ',
      boardingDate: '2027-03-10T08:00',
      returnDate: '2027-03-17T18:00',
      originCityId: 'o1',
      destinationCityId: 'o1',
      pricePerPassenger: 899.9,
      seatsAvailable: 100,
    });
    form.dispatchEvent(new Event('submit'));
    const req = http.expectOne(`${API}/flights`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({
      companyName: 'GOL',
      returnDate: '2027-03-17T18:00:00',
      status: 'ACTIVE',
    });
  });
});

describe('PromotionsPage', () => {
  it('lista só pacotes em promoção', async () => {
    const { http } = await render(PromotionsPage);
    const req = http.expectOne((r) => r.url === `${API}/travel-packages`);
    expect(req.request.params.get('onPromotion')).toBe('true');
  });
});

describe('PricingPage', () => {
  it('mostra de onde vêm as regras e salva com confirmação', async () => {
    const { fixture, http } = await render(PricingPage);
    http.expectOne(`${API}/pricing-settings`).flush({
      marginPercent: 10,
      serviceFeePerPassenger: 0,
      serviceFeeCurrency: 'BRL',
      source: 'ENVIRONMENT',
    });
    await settle(fixture);
    expect(fixture.nativeElement.textContent).toContain('valem as variáveis de ambiente');

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const margin = fixture.nativeElement.querySelector('#margem') as HTMLInputElement;
    margin.value = '12.5';
    margin.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );

    const req = http.expectOne(`${API}/pricing-settings`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      marginPercent: 12.5,
      serviceFeePerPassenger: 0,
      serviceFeeCurrency: 'BRL',
    });
  });
});
