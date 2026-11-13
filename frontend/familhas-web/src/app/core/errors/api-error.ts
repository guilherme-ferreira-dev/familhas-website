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
    /** Código estável de negócio (checkout.yaml, minhas-viagens.yaml). Decide a mensagem; `detail` nunca vai para a tela. */
    readonly code: ProblemCode | null,
    /** Erros de campo do 400 (campo -> mensagem técnica). */
    readonly fieldErrors: Readonly<Record<string, string>>,
    /** Propriedades extras do Problem Details (ex.: bookingId no QUOTE_ALREADY_BOOKED). */
    readonly extra: Readonly<Record<string, unknown>>,
  ) {
    super(`API ${status}${code ? ` ${code}` : ''}`);
    this.name = 'ApiError';
  }

  static from(response: HttpErrorResponse): ApiError {
    const body = isRecord(response.error) ? response.error : {};
    const { code, errors } = body;
    const extra = Object.fromEntries(
      Object.entries(body).filter(([key]) => !PROBLEM_FIELDS.has(key)),
    );
    return new ApiError(
      kindOf(response.status),
      response.status,
      typeof code === 'string' ? (code as ProblemCode) : null,
      isRecord(errors) ? (errors as Record<string, string>) : {},
      extra,
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
  QUOTE_EXPIRED: 'Esta cotação expirou. Gere uma nova para ver os preços atualizados.',
  QUOTE_ALREADY_BOOKED: 'Esta cotação já virou uma reserva.',
  CURRENCY_NOT_SUPPORTED: 'Por enquanto só aceitamos pagamento em reais.',
  SEATS_UNAVAILABLE:
    'Não há mais assentos suficientes neste voo. Tente outra data ou fale com a gente.',
  OFFER_UNAVAILABLE: 'Uma das ofertas deixou de estar disponível. Gere uma nova cotação.',
  BOOKING_NOT_PAYABLE: 'Esta reserva não está mais aguardando pagamento.',
  BOOKING_EXPIRED: 'O prazo para pagar esta reserva acabou. Gere uma nova cotação.',
  PAYMENT_IN_PROGRESS:
    'Já existe um pagamento em processamento para esta reserva. Aguarde a confirmação.',
  PAYMENT_METHOD_UNAVAILABLE: 'Esta forma de pagamento está indisponível agora. Escolha outra.',
  TRIP_NOT_FOUND: 'Não encontramos essa viagem entre as suas.',
  PACKAGE_NOT_FOUND: 'Esse pacote saiu do catálogo e não pode ser favoritado.',
  RATING_NOT_FOUND: 'Não encontramos essa avaliação entre as suas.',
  RATING_ALREADY_EXISTS: 'Você já avaliou esse pacote. Altere a nota que já existe.',
  RATING_NOT_ALLOWED: 'Só dá para avaliar pacotes de viagens que você já fez.',
};

const KIND_MESSAGES: Record<ApiErrorKind, string> = {
  network: 'Não conseguimos falar com o servidor. Verifique sua conexão e tente de novo.',
  validation: 'Alguns dados não estão corretos. Revise e tente de novo.',
  unauthorized: 'Sua sessão terminou. Entre de novo para continuar.',
  forbidden: 'Você não tem permissão para fazer isso.',
  'not-found': 'Não encontramos o que você procurou. Pode ter saído do ar.',
  conflict: 'Essa ação não pode ser feita agora. Atualize a página e tente de novo.',
  unprocessable:
    'Não foi possível calcular essa combinação. Tente outras opções ou fale com a gente.',
  unavailable: 'Um dos nossos fornecedores está fora do ar. Tente de novo em instantes.',
  server: 'Algo deu errado do nosso lado. Tente de novo em instantes.',
};

/** Mensagem amigável em português para qualquer erro (ApiError ou não). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (error.code && CODE_MESSAGES[error.code]) || KIND_MESSAGES[error.kind];
  }
  return KIND_MESSAGES.server;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
