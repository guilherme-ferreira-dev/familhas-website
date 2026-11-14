import { API, page, render, settle } from '../../../testing/setup';
import { ClientsPage } from './clients.page';

describe('ClientsPage', () => {
  it('abre na aba Ativos, pede o segmento e mostra as contagens', async () => {
    const { fixture, http } = await render(ClientsPage);

    http.expectOne(`${API}/clients/segments`).flush({ all: 10, new: 3, active: 4, past: 2 });
    const list = http.expectOne((r) => r.url === `${API}/clients`);
    expect(list.request.params.get('segment')).toBe('ACTIVE');
    list.flush(
      page([
        {
          id: 'c1',
          name: 'Ana Souza',
          email: 'ana@x.com',
          status: 'ACTIVE',
          hasLogin: true,
          favoriteIds: [],
          myFlightIds: [],
        },
      ]),
    );
    await settle(fixture);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Ana Souza');
    expect(text).toMatch(/Ativos\s*4/);
    const current = fixture.nativeElement.querySelector('[aria-current="page"]') as HTMLElement;
    expect(current.textContent).toContain('Ativos');
  });

  it('aba Todos não manda segmento; busca vai junto', async () => {
    const { http } = await render(ClientsPage, { aba: 'ALL', q: 'ana' });

    http.expectOne(`${API}/clients/segments`).flush({ all: 0, new: 0, active: 0, past: 0 });
    const list = http.expectOne((r) => r.url === `${API}/clients`);
    expect(list.request.params.has('segment')).toBe(false);
    expect(list.request.params.get('q')).toBe('ana');
  });
});
