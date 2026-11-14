import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** Catálogo curado: abas de voos, hotéis, quartos, carros, pacotes, promoções e margem. */
@Component({
  selector: 'app-catalog-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Catálogo</h1>
          <p>Preços cadastrados são custo; a margem entra na busca e na cotação.</p>
        </div>
      </div>
      <nav class="tabs" aria-label="Seções do catálogo">
        @for (tab of tabs; track tab.path) {
          <a
            [routerLink]="tab.path"
            routerLinkActive
            #rla="routerLinkActive"
            [attr.aria-current]="rla.isActive ? 'page' : null"
          >
            {{ tab.label }}
          </a>
        }
      </nav>
      <router-outlet />
    </section>
  `,
})
export class CatalogShell {
  protected readonly tabs = [
    { path: 'voos', label: 'Voos' },
    { path: 'hoteis', label: 'Hotéis' },
    { path: 'quartos', label: 'Quartos' },
    { path: 'carros', label: 'Carros' },
    { path: 'pacotes', label: 'Pacotes' },
    { path: 'promocoes', label: 'Promoções' },
    { path: 'margem', label: 'Margem e taxa' },
  ];
}
