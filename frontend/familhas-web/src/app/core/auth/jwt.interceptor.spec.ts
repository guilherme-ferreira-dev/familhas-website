import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { AuthService } from './auth.service';
import { isApiRequest, jwtInterceptor } from './jwt.interceptor';

const API = 'http://localhost:8080';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string | null;

  beforeEach(() => {
    token = 'abc';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: API } as AppConfig },
        { provide: AuthService, useValue: { getAccessToken: () => Promise.resolve(token) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function flushOne(url: string) {
    http.get(url).subscribe();
    await Promise.resolve();
    const req = backend.expectOne(url);
    req.flush({});
    return req.request.headers.get('Authorization');
  }

  it('envia o token para a API', async () => {
    expect(await flushOne(`${API}/api/me`)).toBe('Bearer abc');
  });

  it('não envia o token para outras origens', async () => {
    expect(await flushOne('https://checkout.stripe.com/x')).toBeNull();
    expect(await flushOne('http://localhost:8080.evil.com/api/me')).toBeNull();
    expect(await flushOne('/config.json')).toBeNull();
  });

  it('segue sem Authorization quando não há sessão', async () => {
    token = null;
    expect(await flushOne(`${API}/api/search/packages`)).toBeNull();
  });
});

describe('isApiRequest', () => {
  it('exige a URL base seguida de /api/', () => {
    expect(isApiRequest(`${API}/api/cities`, API)).toBe(true);
    expect(isApiRequest(`${API}/actuator/health`, API)).toBe(false);
    expect(isApiRequest(`${API}evil/api/x`, API)).toBe(false);
  });
});
