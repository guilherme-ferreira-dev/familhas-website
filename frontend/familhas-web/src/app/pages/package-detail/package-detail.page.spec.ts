import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { http, HttpResponse } from 'msw';
import { travelPackages } from '../../../mocks/fixtures';
import { useMockApi } from '../../../testing/mock-api';
import { provideTestConfig, TEST_API } from '../../../testing/test-config';
import { AuthService } from '../../core/auth/auth.service';
import { jwtInterceptor } from '../../core/auth/jwt.interceptor';
import { errorInterceptor } from '../../core/errors/error.interceptor';
import { WhatsappContextService } from '../../core/whatsapp/whatsapp';
import { PackageDetailPage } from './package-detail.page';

const pkg = travelPackages[0];

describe('PackageDetailPage', () => {
  const server = useMockApi();
  const authenticated = signal(false);
  const login = vi.fn();
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    login.mockReset();
    authenticated.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'pacotes/:id', component: PackageDetailPage },
            { path: 'cotacoes/:id', children: [] },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideTestConfig({ apiBaseUrl: TEST_API }),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: authenticated,
            login,
            getAccessToken: () => Promise.resolve(authenticated() ? 'mock-token' : null),
          },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  async function open(url: string) {
    await harness.navigateByUrl(url);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(harness.routeNativeElement?.querySelector('h1')).not.toBeNull();
    });
    return harness.routeNativeElement as HTMLElement;
  }

  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ');
  const button = (el: HTMLElement) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('preço final'))!;

  it('mostra voo, hotel, carro e preço promocional, e passa o contexto ao WhatsApp', async () => {
    const el = await open(`/pacotes/${pkg.id}?passageiros=3`);
    expect(text(el)).toContain(pkg.title);
    expect(text(el)).toContain('Azul · BSB → MCZ');
    expect(text(el)).toContain('Resort Pajuçara');
    expect(text(el)).toContain('Tudo incluído');
    expect(text(el)).toContain('Spin (Locadora Litoral)');
    expect(text(el)).toMatch(/De R\$\s4\.290,00 por R\$\s3\.890,00/);
    expect(el.querySelector<HTMLSelectElement>('#pd-pessoas')!.value).toBe('3');
    expect(TestBed.inject(WhatsappContextService).context()).toEqual({
      kind: 'package',
      id: pkg.id,
      title: pkg.title,
    });
  });

  it('sem login, pede para entrar e volta para o mesmo pacote', async () => {
    const el = await open(`/pacotes/${pkg.id}?passageiros=3`);
    expect(button(el).textContent).toContain('Entrar e ver preço final');
    button(el).click();
    expect(login).toHaveBeenCalledWith(`/pacotes/${pkg.id}?passageiros=3`);
  });

  it('logado, gera a cotação e abre a página dela', async () => {
    authenticated.set(true);
    const el = await open(`/pacotes/${pkg.id}`);
    button(el).click();
    await vi.waitFor(() =>
      expect(TestBed.inject(Router).url).toMatch(/^\/cotacoes\/[0-9a-f-]{36}$/),
    );
  });

  it('mostra a mensagem de negócio quando a cotação falha', async () => {
    authenticated.set(true);
    server.use(
      http.post('*/api/me/quotes', () =>
        HttpResponse.json(
          { status: 503, title: 'Service Unavailable', provider: 'hoteldo' },
          { status: 503 },
        ),
      ),
    );
    const el = await open(`/pacotes/${pkg.id}`);
    button(el).click();
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain(
        'fornecedores está fora do ar',
      );
    });
    expect(button(el).disabled).toBe(false);
  });

  it('pacote inexistente mostra "não encontrado" com saída para a busca', async () => {
    const el = await open('/pacotes/00000000-0000-4000-8000-000000000000');
    expect(text(el)).toContain('Pacote não encontrado');
    expect(el.querySelector('a[href^="/busca"]')).not.toBeNull();
  });

  it('favoritar sem sessão leva ao login e volta para o pacote', async () => {
    const el = await open(`/pacotes/${pkg.id}`);
    const heart = el.querySelector<HTMLButtonElement>('app-favorite-button button')!;
    expect(heart.getAttribute('aria-pressed')).toBe('false');
    heart.click();
    expect(login).toHaveBeenCalledWith(`/pacotes/${pkg.id}`);
  });

  it('com sessão, favorita o pacote e marca o coração', async () => {
    authenticated.set(true);
    let favorited: unknown;
    server.use(
      http.put('*/api/me/favorites/:id', ({ params }) => {
        favorited = params['id'];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const el = await open(`/pacotes/${pkg.id}`);
    const heart = () => el.querySelector<HTMLButtonElement>('app-favorite-button button')!;
    heart().click();
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(heart().textContent).toContain('Nos favoritos');
    });
    await vi.waitFor(() => expect(favorited).toBe(pkg.id));
    expect(heart().getAttribute('aria-pressed')).toBe('true');
  });
});

describe('PackageDetailPage: SEO', () => {
  useMockApi();

  it('usa o nome do pacote no título e o caminho dele no canonical', async () => {
    document.title = '';
    document.head.querySelector('link[rel="canonical"]')?.remove();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'pacotes/:id', component: PackageDetailPage }],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideTestConfig({ siteUrl: 'https://familhas.com.br' }),
        { provide: AuthService, useValue: { isAuthenticated: signal(false), login: vi.fn() } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/pacotes/${pkg.id}?passageiros=2`);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href).toBe(
        `https://familhas.com.br/pacotes/${pkg.id}`,
      );
    });
    expect(document.title).toBe(`${pkg.title} | Familhas`);
  });
});
