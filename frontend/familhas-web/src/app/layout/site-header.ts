import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { Brand } from './brand';

@Component({
  selector: 'app-site-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Brand],
  template: `
    <header class="header" [class.header--over]="overHero()">
      <div class="container bar">
        <a routerLink="/" class="brand" aria-label="Familhas, página inicial"><app-brand /></a>
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
            @if (auth.isAuthenticated()) {
              <li>
                <a
                  routerLink="/minhas-viagens"
                  routerLinkActive="active"
                  ariaCurrentWhenActive="page"
                  >Minhas viagens</a
                >
              </li>
            }
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
      background: var(--color-night);
      color: #fff;
    }
    .header .brand,
    .header nav a,
    .header .greeting {
      color: #fff;
    }
    .header nav a.active {
      background: rgb(255 255 255 / 0.18);
      color: #fff;
    }
    .header .button--ghost {
      color: #fff;
      border-color: rgb(255 255 255 / 0.7);
    }
    .header .button--ghost:hover {
      background: rgb(255 255 255 / 0.16);
      color: #fff;
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2) var(--space-3);
      padding-block: var(--space-2);
    }
    .header--over {
      position: absolute;
      inset: 0 0 auto;
      z-index: 20;
      background: transparent;
    }
    .brand {
      font-size: 1.75rem;
      color: var(--color-primary);
      text-decoration: none;
      margin-inline-end: auto;
    }
    nav {
      order: 3;
      width: 100%;
    }
    @media (max-width: 47.99rem) {
      .bar {
        flex-wrap: nowrap;
      }
      nav {
        order: 0;
        width: auto;
      }
      nav a {
        padding-inline: var(--space-2);
      }
      .brand {
        font-size: 1.375rem;
      }
      .bar {
        gap: var(--space-1);
      }
      .account .button {
        padding-inline: var(--space-3);
      }
      .greeting {
        display: none;
      }
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
      background: rgb(29 79 215 / 0.12);
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
  private readonly router = inject(Router);
  /** Na Home o cabeçalho flutua sobre a cena do hero. */
  protected readonly overHero = signal(this.isHome(this.router.url));

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe((e) => this.overHero.set(this.isHome(e.urlAfterRedirects)));
  }

  private isHome(url: string): boolean {
    return url.split(/[?#]/)[0] === '/';
  }
}
