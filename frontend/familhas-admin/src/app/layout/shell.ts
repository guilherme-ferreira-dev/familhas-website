import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';

interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly exact?: boolean;
}

/** Layout das telas do Admin: menu lateral, usuário e conteúdo. */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip-link" href="#conteudo" (click)="skipToContent($event)">Pular para o conteúdo</a>
    <div class="layout">
      <header class="sidebar">
        <div class="brand">
          <span class="logo" aria-hidden="true">F</span>
          <span>Familhas <strong>Admin</strong></span>
          <button
            type="button"
            class="menu-toggle"
            [attr.aria-expanded]="menuOpen()"
            aria-controls="menu-principal"
            (click)="menuOpen.set(!menuOpen())"
          >
            Menu
          </button>
        </div>
        <nav id="menu-principal" aria-label="Principal" [class.open]="menuOpen()">
          <ul>
            @for (item of nav; track item.path) {
              <li>
                <a
                  [routerLink]="item.path"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: !!item.exact }"
                  ariaCurrentWhenActive="page"
                  >{{ item.label }}</a
                >
              </li>
            }
          </ul>
        </nav>
        @if (auth.user(); as user) {
          <div class="user">
            <span>{{ user.username ?? user.name }}</span>
            <button type="button" class="logout" (click)="auth.logout()">Sair</button>
          </div>
        }
      </header>
      <main id="conteudo" #main tabindex="-1">
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    .skip-link {
      position: absolute;
      left: -999px;
      top: 0;
      z-index: 100;
      padding: var(--space-2) var(--space-3);
      background: var(--color-surface);
    }
    .skip-link:focus {
      left: var(--space-2);
    }
    .layout {
      display: grid;
      min-height: 100dvh;
    }
    .sidebar {
      background: var(--color-night);
      color: #e8f0ff;
      display: grid;
      align-content: start;
      gap: var(--space-3);
      padding: var(--space-3);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      font-size: 1.0625rem;
    }
    .logo {
      display: grid;
      place-items: center;
      width: 2rem;
      height: 2rem;
      border-radius: 0.5rem;
      background: var(--color-yellow);
      color: var(--color-night);
      font-weight: 800;
    }
    .menu-toggle {
      margin-inline-start: auto;
      min-height: 2.25rem;
      padding: 0 var(--space-3);
      border: 1px solid rgb(255 255 255 / 0.4);
      border-radius: var(--radius-sm);
      background: none;
      color: inherit;
      font: inherit;
    }
    nav {
      display: none;
    }
    nav.open {
      display: block;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 2px;
    }
    nav a {
      display: block;
      padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-sm);
      color: inherit;
      text-decoration: none;
      border-inline-start: 3px solid transparent;
    }
    nav a:hover {
      background: rgb(255 255 255 / 0.08);
    }
    nav a.active {
      background: rgb(255 255 255 / 0.12);
      border-inline-start-color: var(--color-yellow);
      font-weight: 600;
    }
    .user {
      display: none;
      gap: var(--space-2);
      align-items: center;
      justify-content: space-between;
      padding-top: var(--space-3);
      border-top: 1px solid rgb(255 255 255 / 0.15);
      font-size: 0.875rem;
      word-break: break-all;
    }
    nav.open ~ .user {
      display: flex;
    }
    .logout {
      min-height: 2rem;
      padding: 0 var(--space-2);
      border: 1px solid rgb(255 255 255 / 0.4);
      border-radius: var(--radius-sm);
      background: none;
      color: inherit;
      font: inherit;
      cursor: pointer;
    }
    main {
      min-width: 0;
    }
    main:focus {
      outline: none;
    }
    @media (min-width: 64rem) {
      .layout {
        grid-template-columns: 15rem 1fr;
      }
      .sidebar {
        position: sticky;
        top: 0;
        height: 100dvh;
        grid-template-rows: auto 1fr auto;
      }
      .menu-toggle {
        display: none;
      }
      nav,
      .user {
        display: block;
      }
      .user {
        display: flex;
      }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  protected readonly nav: readonly NavItem[] = [
    { path: '/', label: 'Dashboard', exact: true },
    { path: '/clientes', label: 'Clientes' },
    { path: '/reservas', label: 'Reservas' },
    { path: '/pagamentos', label: 'Pagamentos' },
    { path: '/cotacoes', label: 'Cotações' },
    { path: '/catalogo', label: 'Catálogo' },
    { path: '/auditoria', label: 'Auditoria' },
  ];

  constructor() {
    let first = true;
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.menuOpen.set(false);
        if (first) {
          first = false;
          return;
        }
        // Leitor de tela: depois de navegar, o foco vai para o conteúdo novo.
        this.main().nativeElement.focus({ preventScroll: true });
      });
  }

  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }
}
