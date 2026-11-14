import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { http, HttpResponse } from 'msw';
import { firstValueFrom } from 'rxjs';
import { travelPackages } from '../../../mocks/fixtures';
import { useMockApi } from '../../../testing/mock-api';
import { provideTestAuth } from '../../../testing/test-auth';
import { provideTestConfig } from '../../../testing/test-config';
import { jwtInterceptor } from '../auth/jwt.interceptor';
import { errorInterceptor } from '../errors/error.interceptor';
import { FavoritesStore } from './favorites-store';

describe('FavoritesStore', () => {
  const server = useMockApi();
  const [maceio, , gramado] = travelPackages;

  function setUp(loggedIn: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideTestConfig(),
        provideTestAuth(loggedIn).provider,
      ],
    });
    const store = TestBed.inject(FavoritesStore);
    TestBed.tick();
    return store;
  }

  it('carrega os favoritos quando há sessão', async () => {
    const store = setUp(true);
    await vi.waitFor(() => expect(store.ready()).toBe(true));
    expect(store.has(gramado.id)).toBe(true);
    expect(store.has(maceio.id)).toBe(false);
  });

  it('sem sessão não chama a API e começa vazio', () => {
    const calls = vi.fn();
    server.use(
      http.get('*/api/me/favorites', () => {
        calls();
        return HttpResponse.json([]);
      }),
    );
    const store = setUp(false);
    expect(store.count()).toBe(0);
    expect(calls).not.toHaveBeenCalled();
  });

  it('marca na hora e mantém quando a API aceita', async () => {
    const store = setUp(true);
    await vi.waitFor(() => expect(store.ready()).toBe(true));

    const done = firstValueFrom(store.toggle(maceio.id), { defaultValue: undefined });
    expect(store.has(maceio.id)).toBe(true);
    await done;
    expect(store.has(maceio.id)).toBe(true);
  });

  it('desfaz quando a API recusa', async () => {
    server.use(
      http.put('*/api/me/favorites/:id', () =>
        HttpResponse.json({ status: 404, code: 'PACKAGE_NOT_FOUND' }, { status: 404 }),
      ),
    );
    const store = setUp(true);
    await vi.waitFor(() => expect(store.ready()).toBe(true));

    await expect(
      firstValueFrom(store.toggle(maceio.id), { defaultValue: undefined }),
    ).rejects.toThrow();
    expect(store.has(maceio.id)).toBe(false);
  });

  it('clique durante o carregamento não é desfeito pela lista que chega depois', async () => {
    const store = setUp(true);
    expect(store.ready()).toBe(false);
    store.toggle(maceio.id).subscribe();
    await vi.waitFor(() => expect(store.ready()).toBe(true));
    expect(store.has(maceio.id)).toBe(true);
    expect(store.has(gramado.id)).toBe(true);
  });
});
