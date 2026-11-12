import { computed, inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { AuthService, AuthUser, rememberReturnUrl, takeReturnUrl } from './auth.service';

/** Keycloak (realm familhas, client público familhas-web) via Authorization Code + PKCE (S256). */
@Injectable()
export class OidcAuthService extends AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);

  readonly isAuthenticated = computed(() => this.oidc.authenticated().isAuthenticated);

  readonly user = computed<AuthUser | null>(() => {
    if (!this.isAuthenticated()) return null;
    const claims = (this.oidc.userData().userData ?? {}) as Record<string, unknown>;
    const name =
      text(claims['given_name']) ??
      text(claims['name']) ??
      text(claims['preferred_username']) ??
      'Cliente';
    return { name, email: text(claims['email']) };
  });

  async init(): Promise<void> {
    try {
      const result = await firstValueFrom(this.oidc.checkAuth());
      const returnUrl = takeReturnUrl();
      if (result.isAuthenticated && returnUrl) {
        await this.router.navigateByUrl(returnUrl, { replaceUrl: true });
      }
    } catch (error) {
      // Keycloak fora do ar não pode derrubar a Loja: o catálogo é público.
      console.error('Falha ao verificar a sessão no Keycloak', error);
    }
  }

  login(returnUrl?: string): void {
    rememberReturnUrl(returnUrl ?? this.router.url);
    this.oidc.authorize();
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.oidc.logoff());
    } catch (error) {
      console.error('Falha no logout do Keycloak; encerrando só a sessão local', error);
      this.oidc.logoffLocal();
    }
  }

  async getAccessToken(): Promise<string | null> {
    if (!this.isAuthenticated()) return null;
    const token = await firstValueFrom(this.oidc.getAccessToken());
    return token || null;
  }
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}
