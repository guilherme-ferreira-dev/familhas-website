import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

/** Conta sem papel do CRM ou sem a permissão da tela. A API também nega com 403. */
@Component({
  selector: 'app-no-permission-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <main class="center">
      <div class="card">
        <h1>Sem permissão</h1>
        <p>
          Esta conta não tem acesso a esta parte do CRM da Voe Familhas. Peça o acesso a um
          administrador da equipe ou entre com outra conta.
        </p>
        @if (auth.isTeamMember()) {
          <a class="btn" routerLink="/">Voltar para a visão geral</a>
        }
        @if (auth.isAuthenticated()) {
          <button type="button" class="btn btn--primary" (click)="auth.logout()">
            Sair e entrar com outra conta
          </button>
        } @else {
          <button type="button" class="btn btn--primary" (click)="auth.login('/')">Entrar</button>
        }
      </div>
    </main>
  `,
  styles: `
    .center {
      display: grid;
      place-items: center;
      min-height: 100dvh;
      padding: var(--space-3);
    }
    .card {
      max-width: 30rem;
      display: grid;
      gap: var(--space-3);
    }
    p {
      margin: 0;
    }
  `,
})
export class NoPermissionPage {
  protected readonly auth = inject(AuthService);
}
