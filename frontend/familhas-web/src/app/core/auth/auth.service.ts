import { Signal } from '@angular/core';

export interface AuthUser {
  readonly name: string;
  readonly email: string | null;
}

/**
 * Contrato de autenticação usado pela Loja. A implementação real usa OIDC + PKCE no Keycloak
 * (OidcAuthService); o build `mock` usa MockAuthService. Telas e interceptors dependem só disto.
 */
export abstract class AuthService {
  abstract readonly isAuthenticated: Signal<boolean>;
  abstract readonly user: Signal<AuthUser | null>;

  /** Processa a volta do Keycloak (code + state) e restaura a sessão. Chamado no bootstrap. */
  abstract init(): Promise<void>;

  /** Redireciona para o login. `returnUrl` é uma rota interna da Loja para onde voltar depois. */
  abstract login(returnUrl?: string): void;

  /** Encerra a sessão local e no Keycloak. */
  abstract logout(): Promise<void>;

  /** Access token atual (renovado se preciso) ou null sem sessão. */
  abstract getAccessToken(): Promise<string | null>;
}

const RETURN_URL_KEY = 'familhas.returnUrl';

/** Só aceita caminhos internos ("/..."), nunca URLs absolutas ou "//host": evita open redirect. */
export function safeReturnUrl(url: string | null | undefined): string {
  if (!url || !url.startsWith('/') || url.startsWith('//') || url.startsWith('/\\')) {
    return '/';
  }
  return url;
}

export function rememberReturnUrl(
  url: string | undefined,
  storage: Storage = sessionStorage,
): void {
  try {
    storage.setItem(RETURN_URL_KEY, safeReturnUrl(url));
  } catch {
    // sem storage (modo privado restrito): volta para a Home
  }
}

export function takeReturnUrl(storage: Storage = sessionStorage): string | null {
  try {
    const url = storage.getItem(RETURN_URL_KEY);
    storage.removeItem(RETURN_URL_KEY);
    return url === null ? null : safeReturnUrl(url);
  } catch {
    return null;
  }
}
