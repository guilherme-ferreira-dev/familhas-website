import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-site-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="header">
      <div class="container bar">
        <a routerLink="/" class="brand" aria-label="Familhas, página inicial">Familhas</a>
        <nav aria-label="Principal">
          <ul>
            <li>
              <a
                routerLink="/"
                routerLinkActive="active"
                [routerLinkActiveOptions]="{ exact: true }"
                ariaCurrentWhenActive="page"
                >Início</a
              >
            </li>
            <li>
              <a routerLink="/busca" routerLinkActive="active" ariaCurrentWhenActive="page"
                >Buscar</a
              >
            </li>
          </ul>
        </nav>
        <div class="account">
          @if (auth.user(); as user) {
            <span class="greeting">Olá, {{ user.name }}</span>
            <button type="button" class="button button--ghost" (click)="auth.logout()">Sair</button>
          } @else {
            <button type="button" class="button button--ghost" (click)="auth.login()">
              Entrar
            </button>
          }
        </div>
      </div>
    </header>
  `,
  styles: `
    .header {
      background: var(--color-surface);
      border-block-end: 1px solid var(--color-border);
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2) var(--space-3);
      padding-block: var(--space-2);
    }
    .brand {
      font-size: 1.375rem;
      font-weight: 800;
      color: var(--color-primary);
      text-decoration: none;
      margin-inline-end: auto;
    }
    nav {
      order: 3;
      width: 100%;
    }
    ul {
      display: flex;
      gap: var(--space-1);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    nav a {
      display: inline-flex;
      align-items: center;
      min-height: var(--tap);
      padding-inline: var(--space-3);
      border-radius: 999px;
      color: var(--color-text);
      text-decoration: none;
      font-weight: 600;
    }
    nav a.active {
      background: rgb(11 93 91 / 0.1);
      color: var(--color-primary);
    }
    .account {
      display: flex;
      align-items: center;
      gap: var(--space-2);
    }
    .greeting {
      display: none;
    }
    @media (min-width: 48rem) {
      nav {
        order: 0;
        width: auto;
      }
      .greeting {
        display: inline;
      }
    }
  `,
})
export class SiteHeader {
  protected readonly auth = inject(AuthService);
}
