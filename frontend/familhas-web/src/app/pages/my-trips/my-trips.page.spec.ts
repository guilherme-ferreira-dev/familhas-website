import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { http, HttpResponse } from 'msw';
import { travelPackages } from '../../../mocks/fixtures';
import { useMockApi } from '../../../testing/mock-api';
import { provideTestAuth } from '../../../testing/test-auth';
import { provideTestConfig } from '../../../testing/test-config';
import { jwtInterceptor } from '../../core/auth/jwt.interceptor';
import { errorInterceptor } from '../../core/errors/error.interceptor';
import { MyTripsPage } from './my-trips.page';

describe('MyTripsPage', () => {
  const server = useMockApi();
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'minhas-viagens', component: MyTripsPage }],
          withComponentInputBinding(),
        ),
        provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
        provideTestConfig(),
        provideTestAuth(true).provider,
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ');

  async function open(url: string, ready: (el: HTMLElement) => boolean) {
    await harness.navigateByUrl(url);
    const el = () => harness.routeNativeElement as HTMLElement;
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(ready(el())).toBe(true);
    });
    return el();
  }

  async function until(el: HTMLElement, check: (el: HTMLElement) => boolean) {
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(check(el)).toBe(true);
    });
  }

  it('sem viagens futuras, convida a buscar um pacote', async () => {
    const el = await open('/minhas-viagens', (e) => text(e).includes('ainda não tem viagens'));
    expect(el.querySelector('a[href="/busca"]')).not.toBeNull();
    expect(
      el.querySelector('nav[aria-label="Viagens"] [aria-current="page"]')?.textContent,
    ).toContain('Próximas');
  });

  it('mostra a viagem passada com status de pagamento e parcelas', async () => {
    const el = await open('/minhas-viagens?aba=passadas', (e) => text(e).includes('Porto Seguro'));
    const trip = el.querySelector('.trip') as HTMLElement;
    expect(text(trip)).toContain('Realizada');
    expect(text(trip)).toContain('05/07/2026 a 12/07/2026');
    expect(text(trip)).toContain('3 pessoas');
    expect(text(trip)).toContain('Pago · Pix');
    expect(text(trip)).toMatch(/À vista: 1x de R\$\s8\.940,00/);
    expect(trip.querySelector('legend')?.textContent).toContain('Como foi a viagem?');
  });

  it('avalia a viagem passada e a nota aparece na viagem e em "Suas avaliações"', async () => {
    let sent: unknown;
    server.use(
      http.post('*/api/me/ratings', async ({ request }) => {
        sent = await request.clone().json();
        return undefined;
      }),
    );
    const el = await open('/minhas-viagens?aba=passadas', (e) => !!e.querySelector('form.rate'));
    // Opções de 1 a 5, nesta ordem; a 4ª é a nota 4.
    const four = el.querySelectorAll<HTMLInputElement>('form.rate input[type="radio"]')[3];
    four.click();
    harness.detectChanges();
    el.querySelector('form.rate')!.dispatchEvent(new Event('submit'));

    await until(el, (e) => text(e).includes('Sua avaliação'));
    expect(sent).toEqual({ rating: 4, travelPackageId: travelPackages[1].id });
    expect(el.querySelector('form.rate')).toBeNull();
    await until(el, (e) => !!e.querySelector('.ratings .rating'));
    expect(text(el.querySelector('.ratings') as HTMLElement)).toContain('Nota 4 de 5');
    expect(text(el)).toContain('nota 4 de 5');
  });

  it('erro de regra na avaliação mostra a mensagem pelo code, sem o detail', async () => {
    server.use(
      http.post('*/api/me/ratings', () =>
        HttpResponse.json(
          { status: 422, code: 'RATING_NOT_ALLOWED', detail: 'texto técnico' },
          { status: 422 },
        ),
      ),
    );
    const el = await open('/minhas-viagens?aba=passadas', (e) => !!e.querySelector('form.rate'));
    el.querySelector('form.rate')!.dispatchEvent(new Event('submit'));

    await until(el, (e) => !!e.querySelector('form.rate [role="alert"]'));
    expect(el.querySelector('form.rate [role="alert"]')!.textContent).toContain(
      'Só dá para avaliar pacotes de viagens que você já fez.',
    );
    expect(text(el)).not.toContain('texto técnico');
  });

  it('lista favoritos e tira da lista ao desmarcar o coração', async () => {
    // Responde aqui para o DELETE não ficar em voo e cair no estado do próximo teste.
    const removed = vi.fn();
    server.use(
      http.delete('*/api/me/favorites/:id', ({ params }) => {
        removed(params['id']);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const el = await open('/minhas-viagens', (e) => text(e).includes('Inverno em Gramado'));
    const favorites = () =>
      el.querySelector('section[aria-labelledby="favoritos-titulo"]') as HTMLElement;
    const heart = favorites().querySelector<HTMLButtonElement>(
      'button[aria-label="Tirar Inverno em Gramado dos favoritos"]',
    )!;
    expect(heart.getAttribute('aria-pressed')).toBe('true');

    heart.click();
    await until(el, () => text(favorites()).includes('Toque no coração'));
    await vi.waitFor(() => expect(removed).toHaveBeenCalledWith(travelPackages[2].id));
  });

  it('sugere pacotes com os motivos e sem repetir os favoritos', async () => {
    const el = await open('/minhas-viagens', (e) => !!e.querySelector('.reasons'));
    const suggestions = el.querySelector(
      'section[aria-labelledby="sugestoes-titulo"]',
    ) as HTMLElement;
    expect(text(suggestions)).toContain('Maceió com tudo incluído');
    expect(text(suggestions)).toContain('Combina com suas viagens');
    expect(text(suggestions)).not.toContain('Inverno em Gramado');
  });

  it('falha ao carregar as viagens oferece tentar de novo', async () => {
    server.use(
      http.get('*/api/me/trips', () => HttpResponse.json({ status: 503 }, { status: 503 })),
    );
    const el = await open('/minhas-viagens', (e) => !!e.querySelector('[role="alert"]'));
    expect(text(el)).toContain('Tentar de novo');
  });
});
