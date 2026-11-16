import { Signal } from '@angular/core';

export interface AuthUser {
  readonly name: string;
  readonly username: string | null;
}

/** Papéis da equipe no realm voe-familhas-crm (realm_access.roles do access token). ADR 0013. */
export const CRM_ROLES = ['crm-admin', 'crm-gestor', 'crm-atendente', 'crm-marketing'] as const;
/** Administrador do CRM: também vê as telas da Loja, que ficam fora do menu. */
export const CRM_ADMIN_ROLE = 'crm-admin';

/**
 * Contrato de autenticação do CRM. A implementação real usa OIDC + PKCE no Keycloak (realm voe-familhas-crm,
 * client familhas-crm).
 *
 * A checagem de papel aqui é só experiência de uso: quem decide é a API, que responde 403 em /api/crm/** para
 * qualquer token sem papel do CRM (inclusive tokens da Loja).
 */
export abstract class AuthService {
  abstract readonly isAuthenticated: Signal<boolean>;
  /** Tem algum papel do CRM. */
  abstract readonly isTeamMember: Signal<boolean>;
  /** Tem crm-admin. */
  abstract readonly isCrmAdmin: Signal<boolean>;
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

const RETURN_URL_KEY = 'familhas-crm.returnUrl';

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
