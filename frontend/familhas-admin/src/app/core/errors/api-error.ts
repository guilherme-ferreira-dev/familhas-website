import { HttpErrorResponse } from '@angular/common/http';
import type { ProblemCode } from '../api/api-types';

export type ApiErrorKind =
  | 'network'
  | 'validation'
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'unprocessable'
  | 'unavailable'
  | 'server';

/** Erro da API já normalizado a partir do Problem Details (RFC 7807). */
export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    readonly status: number,
    /** Código estável de regra de negócio. */
    readonly code: string | null,
    /** Erros de campo do 400 (campo -> mensagem técnica). */
    readonly fieldErrors: Readonly<Record<string, string>>,
    /** Propriedades extras do Problem Details (ex.: maxManualDiscount). */
    readonly extra: Readonly<Record<string, unknown>>,
    /** `detail` da API. No Admin pode aparecer como complemento (é equipe interna), nunca sozinho. */
    readonly detail: string | null,
  ) {
    super(`API ${status}${code ? ` ${code}` : ''}`);
    this.name = 'ApiError';
  }

  static from(response: HttpErrorResponse): ApiError {
    const body = isRecord(response.error) ? response.error : {};
    const { code, errors, detail } = body;
    const extra = Object.fromEntries(
      Object.entries(body).filter(([key]) => !PROBLEM_FIELDS.has(key)),
    );
    return new ApiError(
      kindOf(response.status),
      response.status,
      typeof code === 'string' ? code : null,
      isRecord(errors) ? (errors as Record<string, string>) : {},
      extra,
      typeof detail === 'string' ? detail : null,
    );
  }
}

export function kindOf(status: number): ApiErrorKind {
  if (status === 0) return 'network';
  if (status === 400) return 'validation';
  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'not-found';
  if (status === 409) return 'conflict';
  if (status === 422) return 'unprocessable';
  if (status === 502 || status === 503 || status === 504) return 'unavailable';
  return 'server';
}

const PROBLEM_FIELDS = new Set(['type', 'title', 'status', 'detail', 'instance', 'code', 'errors']);

const CODE_MESSAGES: Record<ProblemCode, string> = {
  QUOTE_BELOW_COST: 'O desconto deixaria a cotação abaixo do custo.',
  CLIENT_INACTIVE: 'Cliente inativo não pode receber cotação. Reative o cliente antes.',
};

const KIND_MESSAGES: Record<ApiErrorKind, string> = {
  network: 'Não conseguimos falar com a API. Verifique a conexão e tente de novo.',
  validation: 'Alguns campos não estão corretos. Revise e tente de novo.',
  unauthorized: 'Sua sessão terminou. Entre de novo para continuar.',
  forbidden: 'Sua conta não tem permissão de administrador.',
  'not-found': 'Registro não encontrado. Pode ter sido excluído.',
  conflict: 'Não foi possível concluir: o registro está ligado a outros dados ou já existe.',
  unprocessable: 'A operação não pode ser feita com esses dados.',
  unavailable: 'Um serviço externo está indisponível. Tente de novo em instantes.',
  server: 'Algo deu errado na API. Tente de novo em instantes.',
};

/** Mensagem em português para qualquer erro (ApiError ou não). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (error.code && CODE_MESSAGES[error.code as ProblemCode]) || KIND_MESSAGES[error.kind];
  }
  return KIND_MESSAGES.server;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
