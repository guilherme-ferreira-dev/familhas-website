import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { APP_CONFIG } from '../core/config/app-config';
import { safeReturnUrl } from '../core/auth/auth.service';

/**
 * O Keycloak não respondeu ao abrir o Admin: fora do ar, ou (no ambiente local) certificado self-signed ainda não
 * aceito pelo navegador. Sem login não há Admin; "Tentar de novo" recarrega a rota pedida.
 */
@Component({
  selector: 'app-login-unavailable-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="center">
      <div class="card" role="alert">
        <h1>Não foi possível falar com o login</h1>
        <p>
          O servidor de login (Keycloak) não respondeu. Pode estar fora do ar ou reiniciando. Espere
          um pouco e tente de novo.
        </p>
        <p class="muted">
          No ambiente local, confira se o Keycloak está rodando e se o navegador aceitou o
          certificado de
          <a [href]="authority" target="_blank" rel="noopener">{{ authority }}</a>.
        </p>
        <div>
          <button type="button" class="btn btn--primary" (click)="retry()">Tentar de novo</button>
        </div>
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
      max-width: 34rem;
      display: grid;
      gap: var(--space-3);
    }
    p {
      margin: 0;
    }
    a {
      word-break: break-all;
    }
  `,
})
export class LoginUnavailablePage {
  /** ?voltar= rota interna que o usuário tinha pedido. */
  readonly voltar = input<string>();

  protected readonly authority = inject(APP_CONFIG).oidc.authority;
  private readonly target = computed(() => safeReturnUrl(this.voltar()));

  /** Recarga completa: o bootstrap tenta o Keycloak de novo. */
  protected retry(): void {
    window.location.assign(this.target());
  }
}
