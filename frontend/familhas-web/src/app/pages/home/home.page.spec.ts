import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { cities, CITY } from '../../../mocks/fixtures';
import { provideTestConfig, TEST_API } from '../../../testing/test-config';
import { HomePage } from './home.page';

const offer = {
  id: 'p1',
  title: 'Maceió com tudo incluído',
  imageUrl: null,
  categoryId: 'cat',
  originCityId: CITY.brasilia,
  destinationCityId: CITY.maceio,
  flightId: 'f1',
  departureAt: '2027-01-10T08:00:00',
  returnAt: '2027-01-17T18:30:00',
  passengers: 1,
  pricePerPerson: 3890,
  originalPricePerPerson: 4290,
  totalPrice: 3890,
  currency: 'BRL',
  seatsAvailable: 3,
};

describe('HomePage', () => {
  let backend: HttpTestingController;

  async function render() {
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTestConfig(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    return fixture;
  }

  function flushCatalog() {
    backend.expectOne((r) => r.url === `${TEST_API}/api/cities`).flush({ content: cities });
    backend.expectOne((r) => r.url === `${TEST_API}/api/categories`).flush({ content: [] });
  }

  it('mostra as ofertas com preço promocional e cidades por nome', async () => {
    const fixture = await render();
    backend
      .expectOne((r) => r.url === `${TEST_API}/api/search/packages`)
      .flush({ content: [offer] });
    flushCatalog();
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    expect(text).toContain('Maceió com tudo incluído');
    expect(text).toContain('Brasília → Maceió');
    expect(text).toMatch(/De R\$\s4\.290,00 por R\$\s3\.890,00/);
    expect(text).toContain('Últimos 3 lugares');
  });

  it('mostra erro com "tentar de novo" e recarrega', async () => {
    const fixture = await render();
    backend
      .expectOne((r) => r.url === `${TEST_API}/api/search/packages`)
      .flush({}, { status: 500, statusText: 'x' });
    flushCatalog();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    TestBed.tick();
    backend.expectOne((r) => r.url === `${TEST_API}/api/search/packages`).flush({ content: [] });
    await fixture.whenStable();
    expect(el.textContent).toContain('Nenhuma oferta no momento');
  });

  it('a busca rápida leva para /busca com destino e pessoas', async () => {
    const fixture = await render();
    backend.expectOne((r) => r.url === `${TEST_API}/api/search/packages`).flush({ content: [] });
    flushCatalog();
    await fixture.whenStable();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      '#home-destino',
    )!;
    select.value = CITY.maceio;
    select.dispatchEvent(new Event('change'));
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    expect(navigate).toHaveBeenCalledWith(['/busca'], {
      queryParams: { tipo: 'pacotes', destino: CITY.maceio, passageiros: 2 },
    });
  });
});
