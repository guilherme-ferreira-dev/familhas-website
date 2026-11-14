import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { cities, CITY } from '../../../mocks/fixtures';
import { provideTestAuth } from '../../../testing/test-auth';
import { provideTestConfig, TEST_API } from '../../../testing/test-config';
import { SearchPage } from './search.page';

const pkg = {
  id: 'p1',
  title: 'Maceió com tudo incluído',
  imageUrl: null,
  categoryId: 'cat',
  originCityId: CITY.brasilia,
  destinationCityId: CITY.maceio,
  flightId: 'f1',
  departureAt: '2027-01-10T08:00:00',
  returnAt: null,
  passengers: 2,
  pricePerPerson: 3890,
  originalPricePerPerson: null,
  totalPrice: 7780,
  currency: 'BRL',
  seatsAvailable: 12,
};

describe('SearchPage', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'busca', component: SearchPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTestConfig(),
        provideTestAuth().provider,
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => backend.verify());

  async function open(url: string) {
    await harness.navigateByUrl(url);
    backend
      .match((r) => r.url === `${TEST_API}/api/cities`)
      .forEach((r) => r.flush({ content: cities }));
    backend
      .match((r) => r.url === `${TEST_API}/api/categories`)
      .forEach((r) => r.flush({ content: [] }));
    harness.detectChanges();
    return harness.routeNativeElement as HTMLElement;
  }

  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ');

  it('não chama a API de voos sem os campos obrigatórios e diz o que falta', async () => {
    const el = await open('/busca?tipo=voos&origem=' + CITY.brasilia);
    backend.expectNone((r) => r.url.includes('/api/search/'));
    expect(text(el)).toContain('Preencha destino, data de ida para ver os resultados');
    expect(el.querySelector('a[aria-current="page"]')?.textContent?.trim()).toBe('Voos');
  });

  it('busca pacotes com os filtros da URL e avisa resultado parcial', async () => {
    const el = await open(`/busca?tipo=pacotes&destino=${CITY.maceio}&passageiros=2`);
    const req = backend.expectOne((r) => r.url === `${TEST_API}/api/search/packages`);
    expect(req.request.params.get('destinationCityId')).toBe(CITY.maceio);
    expect(req.request.params.get('passengers')).toBe('2');
    expect(req.request.params.get('size')).toBe('12');
    req.flush({
      content: [pkg],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
      unavailableProviders: ['hoteldo'],
    });
    await harness.fixture.whenStable();
    expect(text(el)).toContain('Maceió com tudo incluído');
    expect(text(el)).toContain('1 resultado encontrado');
    expect(el.querySelector('[role="status"]')?.textContent).toContain('incompletos');
  });

  it('não envia o formulário com volta antes da ida e marca o campo inválido', async () => {
    const el = await open('/busca?tipo=hoteis');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    const ida = el.querySelector<HTMLInputElement>('#f-ida')!;
    const volta = el.querySelector<HTMLInputElement>('#f-volta')!;
    ida.value = '2027-01-10';
    ida.dispatchEvent(new Event('input'));
    volta.value = '2027-01-08';
    volta.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    harness.detectChanges();
    expect(navigate).not.toHaveBeenCalled();
    expect(volta.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#f-volta-erro')?.textContent).toContain('depois da ida');
  });

  it('pagina pela URL', async () => {
    const el = await open('/busca?tipo=pacotes');
    backend
      .expectOne((r) => r.url === `${TEST_API}/api/search/packages`)
      .flush({
        content: [pkg],
        page: 0,
        size: 12,
        totalElements: 30,
        totalPages: 3,
        unavailableProviders: [],
      });
    await harness.fixture.whenStable();
    const next = [...el.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Próxima'),
    )!;
    next.click();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toContain('pagina=2'));
    harness.detectChanges();
    const req = backend.expectOne((r) => r.url === `${TEST_API}/api/search/packages`);
    expect(req.request.params.get('page')).toBe('1');
    req.flush({
      content: [],
      page: 1,
      size: 12,
      totalElements: 30,
      totalPages: 3,
      unavailableProviders: [],
    });
  });
});
