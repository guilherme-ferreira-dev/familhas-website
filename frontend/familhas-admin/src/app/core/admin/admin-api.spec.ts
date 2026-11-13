import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { fileNameFrom } from './download';
import { queryParams } from './query-params';

describe('queryParams', () => {
  it('ignora vazios e repete listas', () => {
    expect(
      queryParams({
        status: ['SUCCEEDED', 'REFUNDED'],
        method: null,
        q: '',
        page: 0,
        from: '2027-01-01',
      }).toString(),
    ).toBe('status=SUCCEEDED&status=REFUNDED&page=0&from=2027-01-01');
  });
});

describe('fileNameFrom', () => {
  it('usa o nome da API sem permitir caminho', () => {
    expect(fileNameFrom('attachment; filename="pagamentos-20270115-1005.csv"', 'x')).toBe(
      'pagamentos-20270115-1005.csv',
    );
    expect(fileNameFrom('attachment; filename="../../etc/passwd"', 'x')).toBe('.._.._etc_passwd');
    expect(fileNameFrom(null, 'pagamentos.xlsx')).toBe('pagamentos.xlsx');
    expect(new HttpResponse({ headers: new HttpHeaders() }).headers.get('x')).toBeNull();
  });
});
