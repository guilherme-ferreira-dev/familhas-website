import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { APP_CONFIG } from '../config/app-config';

export const DEFAULT_DESCRIPTION =
  'Pacotes de viagem em família com voo, hotel e carro, preço fechado e atendimento humano pelo WhatsApp.';

export interface SeoPage {
  title: string;
  description?: string;
  /** Caminho sem query string (canonical). */
  path: string;
  /** Rotas do cliente (cotação, checkout) não devem ser indexadas. */
  noindex?: boolean;
}

/** Título, description, canonical, robots e Open Graph. A Loja é SPA: isso cobre o básico do guia. */
@Injectable({ providedIn: 'root' })
export class Seo {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly siteUrl = inject(APP_CONFIG).siteUrl;

  apply(page: SeoPage): void {
    const description = page.description ?? DEFAULT_DESCRIPTION;
    const url = `${this.siteUrl}${page.path}`;
    this.title.setTitle(page.title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({
      name: 'robots',
      content: page.noindex ? 'noindex, nofollow' : 'index, follow',
    });
    this.meta.updateTag({ property: 'og:title', content: page.title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:locale', content: 'pt_BR' });
    this.meta.updateTag({ property: 'og:site_name', content: 'Familhas' });
    this.canonical(url);
  }

  private canonical(url: string): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.rel = 'canonical';
      this.document.head.appendChild(link);
    }
    link.href = url;
  }
}

/**
 * Aplica o SEO de cada rota a partir de `title` e de `data.description` / `data.noindex`.
 * Páginas com conteúdo dinâmico (pacote) chamam Seo.apply() de novo quando os dados chegam.
 */
@Injectable()
export class SeoTitleStrategy extends TitleStrategy {
  private readonly seo = inject(Seo);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    let route = snapshot.root;
    while (route.firstChild) route = route.firstChild;
    this.seo.apply({
      title: this.buildTitle(snapshot) ?? 'Familhas',
      description: route.data['description'] as string | undefined,
      noindex: route.data['noindex'] === true,
      path: snapshot.url.split(/[?#]/)[0],
    });
  }
}
