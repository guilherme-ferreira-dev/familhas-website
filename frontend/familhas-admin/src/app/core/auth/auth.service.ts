import { Signal } from '@angular/core';

export interface AuthUser {
  readonly name: string;
  readonly username: string | null;
}

/** Papel exigido para usar o Admin (realm_access.roles do access token). */
export const ADMIN_ROLE = 'ROLE_ADMIN';

/**
 * Contrato de autenticação do Admin. A implementação real usa OIDC + PKCE no Keycloak (client familhas-admin).
 *
 * A checagem de papel aqui é só experiência de uso: quem decide é a API, que responde 403 em /api/admin/**
 * para qualquer token sem ROLE_ADMIN.
 */
export abstract class AuthService {
  abstract readonly isAuthenticated: Signal<boolean>;
  abstract readonly isAdmin: Signal<boolean>;
  abstract readonly user: Signal<AuthUser | null>;
  /** O Keycloak não respondeu no bootstrap (fora do ar ou certificado recusado pelo navegador). */
  abstract readonly unavailable: Signal<boolean>;

  /** Processa a volta do Keycloak (code + state) e restaura a sessão. Chamado no bootstrap. */
  abstract init(): Promise<void>;

  /** Redireciona para o login. `returnUrl` é uma rota interna para onde voltar depois. */
  abstract login(returnUrl?: string): void;

  /** Encerra a sessão local e no Keycloak. */
  abstract logout(): Promise<void>;

  /** Access token atual (renovado se preciso) ou null sem sessão. */
  abstract getAccessToken(): Promise<string | null>;
}

const RETURN_URL_KEY = 'familhas-admin.returnUrl';

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
    // sem storage: volta para o dashboard
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

/**
 * Papéis do realm no access token (claim realm_access.roles). Não valida a assinatura: serve só para a tela;
 * a API valida tudo. Token malformado = nenhum papel.
 */
export function realmRoles(accessToken: string | null): string[] {
  if (!accessToken) return [];
  const payload = accessToken.split('.')[1];
  if (!payload) return [];
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = new TextDecoder().decode(
      Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) =>
        c.charCodeAt(0),
      ),
    );
    const roles = (JSON.parse(json) as { realm_access?: { roles?: unknown } }).realm_access?.roles;
    return Array.isArray(roles) ? roles.filter((r): r is string => typeof r === 'string') : [];
  } catch {
    return [];
  }
}
