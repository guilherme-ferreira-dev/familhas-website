import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MOCK_TOKEN_PREFIX } from '../../../mocks/mock-token';
import { AuthService, AuthUser, safeReturnUrl } from './auth.service';

const SESSION_KEY = 'familhas.mockSession';

/** Login simulado do build `mock`: sem Keycloak, sessão guardada no sessionStorage. */
@Injectable()
export class MockAuthService extends AuthService {
  private readonly router = inject(Router);
  private readonly session = signal<AuthUser | null>(readSession());

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly user = this.session.asReadonly();

  async init(): Promise<void> {
    // nada a processar: não há redirect de volta do Keycloak
  }

  login(returnUrl?: string): void {
    const user: AuthUser = { name: 'Cliente', email: 'cliente@familhas.local' };
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(user));
    } catch {
      // sem storage: a sessão dura até recarregar a página
    }
    this.session.set(user);
    void this.router.navigateByUrl(safeReturnUrl(returnUrl ?? this.router.url));
  }

  async logout(): Promise<void> {
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      // ignorado
    }
    this.session.set(null);
    await this.router.navigateByUrl('/');
  }

  async getAccessToken(): Promise<string | null> {
    return this.isAuthenticated() ? `${MOCK_TOKEN_PREFIX}token` : null;
  }
}

function readSession(): AuthUser | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    return null;
  }
}
