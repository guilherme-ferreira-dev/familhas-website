import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import {
  ADMIN_ROLE,
  AuthService,
  AuthUser,
  realmRoles,
  rememberReturnUrl,
  takeReturnUrl,
} from './auth.service';

/** Keycloak (realm familhas, client público familhas-admin) via Authorization Code + PKCE (S256). */
@Injectable()
export class OidcAuthService extends AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);
  private readonly roles = signal<readonly string[]>([]);
  private readonly unreachable = signal(false);
  readonly unavailable = this.unreachable.asReadonly();

  readonly isAuthenticated = computed(() => this.oidc.authenticated().isAuthenticated);
  readonly isAdmin = computed(() => this.isAuthenticated() && this.roles().includes(ADMIN_ROLE));

  readonly user = computed<AuthUser | null>(() => {
    if (!this.isAuthenticated()) return null;
    const claims = (this.oidc.userData().userData ?? {}) as Record<string, unknown>;
    const username = text(claims['preferred_username']);
    return {
      name: text(claims['given_name']) ?? text(claims['name']) ?? username ?? 'Admin',
      username,
    };
  });

  async init(): Promise<void> {
    try {
      const result = await firstValueFrom(this.oidc.checkAuth());
      this.roles.set(realmRoles(result.accessToken ?? null));
      const returnUrl = takeReturnUrl();
      if (result.isAuthenticated && returnUrl) {
        await this.router.navigateByUrl(returnUrl, { replaceUrl: true });
      }
    } catch (error) {
      // Sem Keycloak não há login: as telas mostram "login indisponível" em vez de um erro genérico.
      console.error('Falha ao verificar a sessão no Keycloak', error);
      this.unreachable.set(true);
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
    this.roles.set([]);
  }

  async getAccessToken(): Promise<string | null> {
    if (!this.isAuthenticated()) return null;
    const token = (await firstValueFrom(this.oidc.getAccessToken())) || null;
    // Token renovado pode trazer papéis novos (ex.: ROLE_ADMIN removido no Keycloak).
    this.roles.set(realmRoles(token));
    return token;
  }
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}
