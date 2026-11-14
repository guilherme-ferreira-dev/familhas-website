import { InjectionToken } from '@angular/core';

/**
 * Configuração de runtime do Admin, lida de `config.json` antes do bootstrap.
 * O mesmo build serve qualquer ambiente: só o arquivo muda no deploy. Nunca colocar segredos aqui.
 */
export interface AppConfig {
  /** URL base da familhas-api, sem barra final. O token só é enviado para `{apiBaseUrl}/api/admin/`. */
  readonly apiBaseUrl: string;
  readonly oidc: {
    /** Issuer do realm, ex.: https://localhost:8443/realms/familhas */
    readonly authority: string;
    /** Client público do Admin no Keycloak (familhas-admin). */
    readonly clientId: string;
    readonly scope: string;
  };
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export class InvalidAppConfigError extends Error {
  constructor(problems: string[]) {
    super(`config.json inválido: ${problems.join('; ')}`);
    this.name = 'InvalidAppConfigError';
  }
}

/** Valida o JSON bruto e devolve uma configuração normalizada (sem barra final nas URLs). */
export function parseAppConfig(raw: unknown): AppConfig {
  const problems: string[] = [];
  const root = asRecord(raw);
  const oidc = asRecord(root['oidc']);

  const apiBaseUrl = requiredUrl(root['apiBaseUrl'], 'apiBaseUrl', problems);
  const authority = requiredUrl(oidc['authority'], 'oidc.authority', problems);
  const clientId = requiredText(oidc['clientId'], 'oidc.clientId', problems);
  const scope = requiredText(oidc['scope'], 'oidc.scope', problems);

  if (problems.length > 0) {
    throw new InvalidAppConfigError(problems);
  }
  return { apiBaseUrl, oidc: { authority, clientId, scope } };
}

/** Busca e valida o `config.json`. Chamado em main.ts, antes do bootstrap. */
export async function loadAppConfig(
  url: string,
  fetchFn: typeof fetch = fetch,
): Promise<AppConfig> {
  const response = await fetchFn(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`Não foi possível carregar ${url} (HTTP ${response.status})`);
  }
  return parseAppConfig(await response.json());
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
}

function requiredText(value: unknown, field: string, problems: string[]): string {
  if (typeof value !== 'string' || value.trim() === '') {
    problems.push(`${field} é obrigatório`);
    return '';
  }
  return value.trim();
}

function requiredUrl(value: unknown, field: string, problems: string[]): string {
  const text = requiredText(value, field, problems);
  if (text === '') {
    return '';
  }
  try {
    const url = new URL(text);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      throw new Error();
    }
  } catch {
    problems.push(`${field} deve ser uma URL http(s)`);
    return '';
  }
  return text.replace(/\/+$/, '');
}
