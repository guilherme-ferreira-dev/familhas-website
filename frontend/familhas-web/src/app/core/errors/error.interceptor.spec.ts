import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { ApiError } from './api-error';
import { errorInterceptor } from './error.interceptor';

const API = 'http://localhost:8080';

describe('errorInterceptor', () => {
  const login = vi.fn();
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    login.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: API } as AppConfig },
        { provide: AuthService, useValue: { login } },
        { provide: Router, useValue: { url: '/checkout/1' } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  function fail(url: string, status: number, body: object = {}): Promise<unknown> {
    return new Promise((resolve) => {
      http.get(url).subscribe({ error: resolve });
      backend.expectOne(url).flush(body, { status, statusText: 'x' });
    });
  }

  it('converte erro da API em ApiError', async () => {
    const error = await fail(`${API}/api/me/bookings`, 422, { code: 'SEATS_UNAVAILABLE' });
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('SEATS_UNAVAILABLE');
    expect(login).not.toHaveBeenCalled();
  });

  it('401 da API manda para o login e volta para a tela atual', async () => {
    await fail(`${API}/api/me`, 401);
    expect(login).toHaveBeenCalledWith('/checkout/1');
  });

  it('não mexe em erros de outras origens', async () => {
    const error = await fail('/config.json', 404);
    expect(error).not.toBeInstanceOf(ApiError);
  });
});
