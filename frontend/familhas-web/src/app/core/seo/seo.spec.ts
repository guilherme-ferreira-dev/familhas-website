import { DOCUMENT } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, TitleStrategy } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTestConfig } from '../../../testing/test-config';
import { DEFAULT_DESCRIPTION, SeoTitleStrategy } from './seo';

@Component({ template: '' })
class Blank {}

describe('SeoTitleStrategy', () => {
  let head: HTMLHeadElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'busca',
            component: Blank,
            title: 'Buscar | Familhas',
            data: { description: 'Busque.' },
          },
          {
            path: 'checkout/:id',
            component: Blank,
            title: 'Pagamento | Familhas',
            data: { noindex: true },
          },
        ]),
        provideTestConfig({ siteUrl: 'https://familhas.com.br' }),
        { provide: TitleStrategy, useClass: SeoTitleStrategy },
      ],
    });
    head = TestBed.inject(DOCUMENT).head;
  });

  const meta = (selector: string) =>
    head.querySelector<HTMLMetaElement>(`meta[${selector}]`)?.content;

  it('título, description, canonical sem query e Open Graph', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/busca?tipo=voos');
    expect(TestBed.inject(DOCUMENT).title).toBe('Buscar | Familhas');
    expect(meta('name="description"')).toBe('Busque.');
    expect(meta('name="robots"')).toBe('index, follow');
    expect(meta('property="og:url"')).toBe('https://familhas.com.br/busca');
    expect(head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href).toBe(
      'https://familhas.com.br/busca',
    );
  });

  it('rotas do cliente ficam fora do índice e usam a description padrão', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/checkout/abc');
    expect(meta('name="robots"')).toBe('noindex, nofollow');
    expect(meta('name="description"')).toBe(DEFAULT_DESCRIPTION);
  });
});
