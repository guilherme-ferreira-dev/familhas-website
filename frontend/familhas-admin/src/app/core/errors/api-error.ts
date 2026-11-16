import { HttpErrorResponse } from '@angular/common/http';
import type { ProblemCode } from '../api/api-types';
import type { CrmProblemCode } from '../api/crm-types';

export type ApiErrorKind =
  | 'network'
  | 'validation'
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'unprocessable'
  | 'rate-limited'
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
  if (status === 429) return 'rate-limited';
  if (status === 502 || status === 503 || status === 504) return 'unavailable';
  return 'server';
}

const PROBLEM_FIELDS = new Set(['type', 'title', 'status', 'detail', 'instance', 'code', 'errors']);

const CODE_MESSAGES: Record<ProblemCode | CrmProblemCode, string> = {
  QUOTE_BELOW_COST: 'O desconto deixaria a cotação abaixo do custo.',
  CLIENT_INACTIVE: 'Cliente inativo não pode receber cotação. Reative o cliente antes.',
  INTEGRATION_NOT_CONFIGURED:
    'Esta integração ainda não está configurada. Preencha os dados manualmente por enquanto.',
  FILE_EMPTY: 'O arquivo está vazio.',
  FILE_TOO_LARGE: 'Arquivo grande demais. Limites: PDF 15 MB, imagens 10 MB, vídeo 100 MB.',
  FILE_TYPE_NOT_ALLOWED: 'Tipo de arquivo não aceito aqui.',
  FILE_TYPE_MISMATCH: 'O conteúdo do arquivo não corresponde à extensão. Confira o arquivo.',
  FILE_NOT_FOUND: 'Arquivo não encontrado. Pode ter sido excluído.',
  FILE_LINK_INVALID: 'O link do arquivo venceu. Abra o arquivo de novo.',
  URL_NOT_ALLOWED: 'Esse endereço não pode ser usado. Use um link público com https.',
  REMOTE_UNAVAILABLE: 'Não foi possível abrir o endereço informado.',
  REMOTE_CONTENT_TOO_LARGE: 'O conteúdo do endereço é grande demais.',
  VERSION_CONFLICT: 'Outra pessoa alterou este registro. Recarregue a página antes de salvar.',
  CPF_INVALID: 'CPF inválido. Confira os números.',
  RETURN_BEFORE_OUTBOUND: 'A volta não pode ser antes da ida.',
  TOO_MANY_ATTACHMENTS: 'Limite de 20 anexos por voo.',
  CLIENT_FLIGHT_NOT_FOUND: 'Voo não encontrado. Pode ter sido excluído.',
  VOUCHER_FIELDS_MISSING: 'Preencha os campos obrigatórios deste tipo de voucher.',
  VOUCHER_CANCELLED: 'Voucher cancelado não pode ser alterado.',
  VOUCHER_NOT_FOUND: 'Voucher não encontrado.',
  FILE_CATEGORY_MISMATCH: 'Esse arquivo não pode ser usado aqui.',
  INVALID_DATES: 'Datas inválidas: a ida precisa ser futura e a volta depois da ida.',
  FLIGHT_SEARCH_TOO_BROAD: 'Pesquisa grande demais. Escolha menos destinos ou meses.',
  FLIGHT_SEARCH_QUOTA: 'Limite de pesquisas por hora atingido. Tente mais tarde.',
  FLIGHT_SEARCH_FAILED: 'A pesquisa de voos não respondeu. Tente de novo em instantes.',
  ORIGIN_NOT_ALLOWED: 'Origem fora da lista configurada.',
  SAME_ORIGIN_DESTINATION: 'O destino não pode ser igual à origem.',
  DUPLICATED_DESTINATION: 'Destino repetido.',
  GROUP_NAME_TAKEN: 'Já existe um grupo com esse nome.',
  INVALID_POST_SHAPE:
    'Formato incompleto: Story leva 1 imagem, Feed de 1 a 20 e Reel um vídeo MP4.',
  TOO_MANY_HASHTAGS: 'O Instagram aceita no máximo 30 hashtags.',
  DUPLICATED_MEDIA: 'A mesma imagem aparece em duas lâminas.',
  CAPTION_REQUIRED: 'Escreva a legenda antes de marcar como pronto.',
  POST_LOCKED: 'Post publicado, publicando ou em conferência não pode ser alterado.',
  POST_NOT_UNCERTAIN: 'Este post não está aguardando conferência.',
  PUBLISH_NOT_ALLOWED: 'Revise e marque o post como pronto. Se já foi enviado, aguarde ou confira.',
  IMAGE_NOT_JPEG: 'A Meta só aceita JPEG. Gere as lâminas de novo nesta tela.',
  CAROUSEL_TOO_LARGE: 'A Meta aceita menos lâminas por carrossel. Divida em dois posts.',
  CAPTION_TOO_LONG: 'Legenda com hashtags passa de 2.200 caracteres.',
  SOURCE_REQUIRED: 'Envie o PDF ou informe o link do orçamento.',
  DAY_OUT_OF_RANGE: 'Há um dia do roteiro além da quantidade de dias.',
};

const KIND_MESSAGES: Record<ApiErrorKind, string> = {
  network: 'Não conseguimos falar com a API. Verifique a conexão e tente de novo.',
  validation: 'Alguns campos não estão corretos. Revise e tente de novo.',
  unauthorized: 'Sua sessão terminou. Entre de novo para continuar.',
  forbidden: 'Sua conta não tem permissão para esta ação.',
  'not-found': 'Registro não encontrado. Pode ter sido excluído.',
  conflict: 'Não foi possível concluir: o registro está ligado a outros dados ou já existe.',
  unprocessable: 'A operação não pode ser feita com esses dados.',
  'rate-limited': 'Muitas requisições em pouco tempo. Aguarde alguns segundos e tente de novo.',
  unavailable: 'Um serviço externo está indisponível. Tente de novo em instantes.',
  server: 'Algo deu errado na API. Tente de novo em instantes.',
};

/** Mensagem em português para qualquer erro (ApiError ou não). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      (error.code && CODE_MESSAGES[error.code as ProblemCode | CrmProblemCode]) ||
      KIND_MESSAGES[error.kind]
    );
  }
  return KIND_MESSAGES.server;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
