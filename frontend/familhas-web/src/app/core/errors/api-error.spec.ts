import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, errorMessage, kindOf } from './api-error';

const response = (status: number, error: unknown) =>
  new HttpErrorResponse({ status, error, url: 'http://api/api/x' });

describe('ApiError', () => {
  it('lê code, errors e propriedades extras do Problem Details', () => {
    const error = ApiError.from(
      response(409, {
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'Esta cotacao ja virou reserva.',
        code: 'QUOTE_ALREADY_BOOKED',
        bookingId: 'b1',
      }),
    );
    expect(error).toMatchObject({ kind: 'conflict', code: 'QUOTE_ALREADY_BOOKED' });
    expect(error.extra).toEqual({ bookingId: 'b1' });
  });

  it('lê erros de campo do 400', () => {
    const error = ApiError.from(
      response(400, { status: 400, errors: { passengers: 'must be <= 9' } }),
    );
    expect(error.fieldErrors).toEqual({ passengers: 'must be <= 9' });
  });

  it('aguenta corpo que não é Problem Details (proxy, HTML)', () => {
    const error = ApiError.from(response(502, '<html>Bad gateway</html>'));
    expect(error).toMatchObject({ kind: 'unavailable', code: null });
  });

  it.each([
    [0, 'network'],
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [404, 'not-found'],
    [422, 'unprocessable'],
    [503, 'unavailable'],
    [500, 'server'],
  ])('status %i -> %s', (status, kind) => {
    expect(kindOf(status)).toBe(kind);
  });
});

describe('errorMessage', () => {
  it('prefere a mensagem do code e nunca mostra o detail da API', () => {
    const error = ApiError.from(response(409, { detail: 'texto tecnico', code: 'QUOTE_EXPIRED' }));
    expect(errorMessage(error)).toContain('cotação expirou');
    expect(errorMessage(error)).not.toContain('texto tecnico');
  });

  it('cai na mensagem do tipo de erro quando não há code', () => {
    expect(errorMessage(ApiError.from(response(0, null)))).toContain('conexão');
  });

  it('tem mensagem genérica para erro que não veio da API', () => {
    expect(errorMessage(new TypeError('x'))).toContain('Algo deu errado');
  });
});
