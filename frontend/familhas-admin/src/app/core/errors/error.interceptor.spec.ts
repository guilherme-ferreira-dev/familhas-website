import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { ApiError, errorMessage } from './api-error';
import { errorInterceptor } from './error.interceptor';

const API = 'http://localhost:8080';

describe('errorInterceptor', () => {
  const login = vi.fn();
  const navigateByUrl = vi.fn();
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    login.mockReset();
    navigateByUrl.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: API } as AppConfig },
        { provide: AuthService, useValue: { login } },
        { provide: Router, useValue: { url: '/pagamentos', navigateByUrl } },
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

  it('lê code e propriedades extras do Problem Details', async () => {
    const error = (await fail(`${API}/api/admin/quotes`, 422, {
      code: 'QUOTE_BELOW_COST',
      detail: 'Maximo: 482.95 BRL',
      maxManualDiscount: 482.95,
    })) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('QUOTE_BELOW_COST');
    expect(error.extra).toEqual({ maxManualDiscount: 482.95 });
    expect(errorMessage(error)).toContain('abaixo do custo');
  });

  it('401 manda para o login e 403 para "sem permissão"', async () => {
    await fail(`${API}/api/admin/payments`, 401);
    expect(login).toHaveBeenCalledWith('/pagamentos');
    await fail(`${API}/api/admin/payments`, 403);
    expect(navigateByUrl).toHaveBeenCalledWith('/sem-permissao');
  });

  it('não mexe em erros de outras URLs', async () => {
    expect(await fail('/config.json', 404)).not.toBeInstanceOf(ApiError);
  });
});
