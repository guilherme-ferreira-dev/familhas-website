import { InjectionToken } from '@angular/core';

/**
 * Configuração de runtime da Loja, lida de `config.json` antes do bootstrap.
 * O mesmo build serve qualquer ambiente: só o arquivo muda no deploy.
 * Nunca colocar segredos aqui: o arquivo é público.
 */
export interface AppConfig {
  /** URL base da familhas-api, sem barra final. O token só é enviado para ela. */
  readonly apiBaseUrl: string;
  /** URL pública da Loja, usada em canonical e Open Graph. */
  readonly siteUrl: string;
  readonly oidc: {
    /** Issuer do realm, ex.: https://localhost:8443/realms/familhas */
    readonly authority: string;
    readonly clientId: string;
    readonly scope: string;
  };
  readonly whatsapp: {
    /** Número em formato internacional só com dígitos (ex.: 5561999999999). Vazio esconde o botão. */
    readonly phone: string;
    /** Saudação usada quando a tela não tem contexto (pacote, cotação ou reserva). */
    readonly defaultMessage: string;
  };
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export class InvalidAppConfigError extends Error {
  constructor(problems: string[]) {
    super(`config.json inválido: ${problems.join('; ')}`);
    this.name = 'InvalidAppConfigError';
  }
}

const PHONE_PATTERN = /^\d{10,15}$/;

/** Valida o JSON bruto e devolve uma configuração normalizada (sem barra final nas URLs). */
export function parseAppConfig(raw: unknown): AppConfig {
  const problems: string[] = [];
  const root = asRecord(raw);
  const oidc = asRecord(root['oidc']);
  const whatsapp = asRecord(root['whatsapp']);

  const apiBaseUrl = requiredUrl(root['apiBaseUrl'], 'apiBaseUrl', problems);
  const siteUrl = requiredUrl(root['siteUrl'], 'siteUrl', problems);
  const authority = requiredUrl(oidc['authority'], 'oidc.authority', problems);
  const clientId = requiredText(oidc['clientId'], 'oidc.clientId', problems);
  const scope = requiredText(oidc['scope'], 'oidc.scope', problems);
  const defaultMessage = requiredText(
    whatsapp['defaultMessage'],
    'whatsapp.defaultMessage',
    problems,
  );

  const phone = typeof whatsapp['phone'] === 'string' ? whatsapp['phone'].trim() : '';
  if (phone !== '' && !PHONE_PATTERN.test(phone)) {
    problems.push('whatsapp.phone deve ter de 10 a 15 dígitos, sem + ou espaços');
  }

  if (problems.length > 0) {
    throw new InvalidAppConfigError(problems);
  }
  return {
    apiBaseUrl,
    siteUrl,
    oidc: { authority, clientId, scope },
    whatsapp: { phone, defaultMessage },
  };
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
