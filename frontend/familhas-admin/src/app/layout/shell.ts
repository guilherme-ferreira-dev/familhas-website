import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { CrmSession } from '../core/crm/crm-session';
import { visibleNavigation } from './navigation';

/**
 * Layout do CRM: marca com razão social e CNPJ abaixo (JSON, identidade visual), menu nos grupos do JSON filtrado
 * pelas permissões, usuário e conteúdo. A logo oficial (arquivo) ainda não está no repositório: até lá, a marca é
 * a palavra "Voe Familhas" em texto, sem recriar o desenho.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip-link" href="#conteudo" (click)="skipToContent($event)">Pular para o conteúdo</a>
    <div class="layout">
      <header class="sidebar">
        <div class="brand">
          <a class="wordmark" routerLink="/" aria-label="Voe Familhas CRM, visão geral">
            <span class="voe">Voe</span>
            <span class="name">Familhas</span>
          </a>
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
        @if (session.company(); as company) {
          <p class="legal">
            {{ company.legalName }}<br />
            CNPJ <span class="num">{{ company.cnpj }}</span>
          </p>
        }
        <nav id="menu-principal" aria-label="Principal" [class.open]="menuOpen()">
          @for (group of nav(); track group.label) {
            <h2 class="group" [id]="'grupo-' + $index">{{ group.label }}</h2>
            <ul [attr.aria-labelledby]="'grupo-' + $index">
              @for (item of group.items; track item.path) {
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
          }
        </nav>
        @if (auth.user(); as user) {
          <div class="user">
            <span>{{ user.username ?? user.name }}</span>
            <button type="button" class="logout" (click)="logout()">Sair</button>
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
    .wordmark {
      display: grid;
      line-height: 1;
      color: #ffffff;
      text-decoration: none;
      font-weight: 800;
    }
    .wordmark .voe {
      color: var(--color-yellow);
      font-size: 0.9375rem;
    }
    .wordmark .name {
      font-size: 1.375rem;
    }
    .legal {
      margin: 0;
      font-size: 0.75rem;
      line-height: 1.4;
      color: rgb(232 240 255 / 0.8);
    }
    .group {
      margin: var(--space-3) 0 var(--space-1);
      padding-inline-start: var(--space-3);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: rgb(232 240 255 / 0.7);
    }
    .group:first-child {
      margin-top: 0;
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
        grid-template-rows: auto auto 1fr auto;
        overflow-y: auto;
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
  protected readonly session = inject(CrmSession);
  protected readonly nav = computed(() => {
    // Recalcula quando o perfil chega.
    this.session.profile();
    return visibleNavigation((permission) => this.session.can(permission));
  });
  protected readonly menuOpen = signal(false);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    void this.session.ready();
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

  protected logout(): void {
    void this.auth.logout();
  }

  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }
}
