import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { CrmApi } from './crm-api';

const API = 'http://localhost:8080';

describe('CrmApi', () => {
  let api: CrmApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: API } as AppConfig },
      ],
    });
    api = TestBed.inject(CrmApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('busca de voos sem parâmetros vazios', () => {
    api
      .clientFlights({
        q: 'silva joão',
        archived: false,
        from: '',
        airline: null,
        page: 0,
        size: 20,
      })
      .subscribe();
    const req = backend.expectOne((r) => r.url === `${API}/api/crm/client-flights`);
    expect(req.request.params.get('q')).toBe('silva joão');
    expect(req.request.params.get('archived')).toBe('false');
    expect(req.request.params.has('from')).toBe(false);
    expect(req.request.params.has('airline')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('upload vai como multipart com a categoria na query, e ids são escapados', () => {
    api
      .uploadFile('VOUCHER_SOURCE', new File(['%PDF'], 'v.pdf', { type: 'application/pdf' }))
      .subscribe();
    const upload = backend.expectOne((r) => r.url === `${API}/api/crm/files`);
    expect(upload.request.params.get('category')).toBe('VOUCHER_SOURCE');
    expect(upload.request.body).toBeInstanceOf(FormData);
    upload.flush({});

    api.voucherDocument('../x').subscribe();
    backend.expectOne(`${API}/api/crm/vouchers/..%2Fx/document`).flush({});
  });

  it('link assinado vira URL absoluta da API', () => {
    expect(api.absolute('/api/crm/files/1/content?expires=1&signature=a')).toBe(
      `${API}/api/crm/files/1/content?expires=1&signature=a`,
    );
  });
});
