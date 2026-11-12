import type { ParamMap, Params } from '@angular/router';
import type { Query } from '../../core/catalog/catalog-api';

export type SearchKind = 'pacotes' | 'voos' | 'hoteis' | 'carros';
export const SEARCH_KINDS: readonly SearchKind[] = ['pacotes', 'voos', 'hoteis', 'carros'];

export const KIND_LABELS: Record<SearchKind, string> = {
  pacotes: 'Pacotes',
  voos: 'Voos',
  hoteis: 'Hotéis',
  carros: 'Carros',
};

/** Filtros como aparecem na URL da Loja (em português, compartilháveis). */
export interface SearchFilters {
  tipo: SearchKind;
  origem: string;
  destino: string;
  categoria: string;
  ida: string;
  volta: string;
  passageiros: number;
  precoMin: string;
  precoMax: string;
  estrelas: string;
  categoriaCarro: string;
  ordem: 'preco' | 'preco-desc';
  pagina: number;
}

export const PAGE_SIZE = 12;

export function filtersFrom(params: ParamMap): SearchFilters {
  const tipo = params.get('tipo');
  const passageiros = clampInt(params.get('passageiros'), 1, 9, 1);
  return {
    tipo: SEARCH_KINDS.includes(tipo as SearchKind) ? (tipo as SearchKind) : 'pacotes',
    origem: params.get('origem') ?? '',
    destino: params.get('destino') ?? '',
    categoria: params.get('categoria') ?? '',
    ida: isoDate(params.get('ida')),
    volta: isoDate(params.get('volta')),
    passageiros,
    precoMin: money(params.get('precoMin')),
    precoMax: money(params.get('precoMax')),
    estrelas: params.get('estrelas') ?? '',
    categoriaCarro: params.get('categoriaCarro') ?? '',
    ordem: params.get('ordem') === 'preco-desc' ? 'preco-desc' : 'preco',
    pagina: clampInt(params.get('pagina'), 1, 10_000, 1),
  };
}

/** Volta para query params, omitindo o que está no padrão (URL curta). */
export function filtersToParams(f: SearchFilters): Params {
  const params: Params = { tipo: f.tipo };
  const set = (key: string, value: string | number, fallback: string | number = '') => {
    if (value !== fallback && value !== '') params[key] = value;
  };
  set('origem', f.origem);
  set('destino', f.destino);
  set('categoria', f.categoria);
  set('ida', f.ida);
  set('volta', f.volta);
  set('passageiros', f.passageiros, 1);
  set('precoMin', f.precoMin);
  set('precoMax', f.precoMax);
  set('estrelas', f.estrelas);
  set('categoriaCarro', f.categoriaCarro);
  set('ordem', f.ordem, 'preco');
  set('pagina', f.pagina, 1);
  return params;
}

/** O que falta preencher para a busca rodar. Pacotes não têm filtro obrigatório. */
export function missingFields(f: SearchFilters): string[] {
  const missing: string[] = [];
  const need = (ok: boolean, label: string) => !ok && missing.push(label);
  switch (f.tipo) {
    case 'voos':
      need(!!f.origem, 'origem');
      need(!!f.destino, 'destino');
      need(!!f.ida, 'data de ida');
      break;
    case 'hoteis':
      need(!!f.destino, 'cidade');
      need(!!f.ida, 'data de entrada');
      need(!!f.volta, 'data de saída');
      break;
    case 'carros':
      need(!!f.destino, 'cidade');
      need(!!f.ida, 'data de retirada');
      need(!!f.volta, 'data de devolução');
      break;
  }
  return missing;
}

/** Erros de coerência (datas e faixa de preço) mostrados no formulário. */
export function invalidFields(f: SearchFilters): Partial<Record<'volta' | 'precoMax', string>> {
  const errors: Partial<Record<'volta' | 'precoMax', string>> = {};
  if (f.ida && f.volta && f.volta < f.ida) {
    errors.volta = 'A data de volta precisa ser depois da ida.';
  }
  if (f.tipo !== 'voos' && f.tipo !== 'pacotes' && f.ida && f.volta && f.volta === f.ida) {
    errors.volta = 'Escolha pelo menos uma diária.';
  }
  if (f.precoMin && f.precoMax && Number(f.precoMax) < Number(f.precoMin)) {
    errors.precoMax = 'O preço máximo precisa ser maior que o mínimo.';
  }
  return errors;
}

/** Converte os filtros da URL nos parâmetros de cada endpoint de /api/search (familhas-api.yaml). */
export function toApiQuery(f: SearchFilters): Query {
  const common = {
    minPrice: f.precoMin,
    maxPrice: f.precoMax,
    page: f.pagina - 1,
    size: PAGE_SIZE,
    sort: f.ordem === 'preco-desc' ? 'totalPrice,desc' : 'totalPrice,asc',
  };
  switch (f.tipo) {
    case 'pacotes':
      return {
        originCityId: f.origem,
        destinationCityId: f.destino,
        categoryId: f.categoria,
        departureFrom: f.ida,
        departureTo: f.volta,
        passengers: f.passageiros,
        ...common,
      };
    case 'voos':
      return {
        originCityId: f.origem,
        destinationCityId: f.destino,
        departureDate: f.ida,
        returnDate: f.volta,
        passengers: f.passageiros,
        ...common,
      };
    case 'hoteis':
      return {
        cityId: f.destino,
        checkIn: f.ida,
        checkOut: f.volta,
        guests: f.passageiros,
        minStars: f.estrelas,
        ...common,
      };
    case 'carros':
      return {
        cityId: f.destino,
        pickUpDate: f.ida,
        dropOffDate: f.volta,
        passengers: f.passageiros,
        carCategory: f.categoriaCarro,
        ...common,
      };
  }
}

function clampInt(value: string | null, min: number, max: number, fallback: number): number {
  const n = Number.parseInt(value ?? '', 10);
  return Number.isNaN(n) ? fallback : Math.min(max, Math.max(min, n));
}

function isoDate(value: string | null): string {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : '';
}

function money(value: string | null): string {
  return value && /^\d+(\.\d{1,2})?$/.test(value) ? value : '';
}
