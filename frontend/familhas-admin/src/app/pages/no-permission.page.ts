import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';

/** Conta sem ROLE_ADMIN (ex.: cliente da Loja). A API também nega tudo em /api/admin com 403. */
@Component({
  selector: 'app-no-permission-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="center">
      <div class="card">
        <h1>Sem permissão</h1>
        <p>
          Esta conta não tem acesso ao Admin da Familhas. Entre com uma conta de administrador ou
          peça o acesso a quem cuida do Keycloak.
        </p>
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
