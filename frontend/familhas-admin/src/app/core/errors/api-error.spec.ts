import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, errorMessage, kindOf } from './api-error';

const response = (status: number, error: unknown) =>
  new HttpErrorResponse({ status, error, url: 'http://api/api/admin/x' });

describe('ApiError', () => {
  it.each([
    [0, 'network'],
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [429, 'rate-limited'],
    [503, 'unavailable'],
    [500, 'server'],
  ])('status %i -> %s', (status, kind) => {
    expect(kindOf(status)).toBe(kind);
  });

  it('429 do rate limit pede para esperar, sem mostrar o detail da API', () => {
    const error = ApiError.from(
      response(429, { code: 'RATE_LIMITED', detail: 'Muitas requisicoes' }),
    );
    expect(errorMessage(error)).toContain('Aguarde alguns segundos');
    expect(errorMessage(error)).not.toContain('requisicoes');
  });
});
