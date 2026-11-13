import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { AuthService } from './auth.service';
import { isAdminApiRequest, jwtInterceptor } from './jwt.interceptor';

const API = 'http://localhost:8080';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: API } as AppConfig },
        { provide: AuthService, useValue: { getAccessToken: () => Promise.resolve('abc') } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function authorization(url: string) {
    http.get(url).subscribe();
    await Promise.resolve();
    const req = backend.expectOne(url);
    req.flush({});
    return req.request.headers.get('Authorization');
  }

  it('envia o token só para /api/admin', async () => {
    expect(await authorization(`${API}/api/admin/payments`)).toBe('Bearer abc');
    expect(await authorization(`${API}/api/me`)).toBeNull();
    expect(await authorization(`${API}/api/admin.evil.com/x`)).toBeNull();
    expect(await authorization('https://localhost:8443/realms/familhas/x')).toBeNull();
    expect(await authorization('/config.json')).toBeNull();
  });

  it('exige a URL base exata', () => {
    expect(isAdminApiRequest(`${API}evil/api/admin/x`, API)).toBe(false);
  });
});
