import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTestConfig, TEST_API } from '../../../testing/test-config';
import { CatalogApi, toParams } from './catalog-api';

describe('toParams', () => {
  it('descarta filtros vazios', () => {
    const params = toParams({ a: 'x', b: '', c: null, d: undefined, e: 0 });
    expect(params.toString()).toBe('a=x&e=0');
  });
});

describe('CatalogApi', () => {
  let api: CatalogApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTestConfig()],
    });
    api = TestBed.inject(CatalogApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('busca pacotes em /api/search/packages com os filtros', () => {
    api.searchPackages({ destinationCityId: 'c1', passengers: 2, minPrice: '' }).subscribe();
    const req = backend.expectOne((r) => r.url === `${TEST_API}/api/search/packages`);
    expect(req.request.params.toString()).toBe('destinationCityId=c1&passengers=2');
    req.flush({ content: [] });
  });

  it('carrega todas as cidades ativas numa página só', () => {
    api.cities().subscribe();
    const req = backend.expectOne((r) => r.url === `${TEST_API}/api/cities`);
    expect(req.request.params.get('size')).toBe('100');
    req.flush({ content: [] });
  });

  it('codifica o id na URL do detalhe', () => {
    api.travelPackage('a/b').subscribe();
    backend.expectOne(`${TEST_API}/api/travel-packages/a%2Fb`).flush({});
  });
});
