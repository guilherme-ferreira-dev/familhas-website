import type {
  CarRentalOffer,
  Flight,
  Hotel,
  RoomOffer,
  TravelPackage,
  TravelPackageComponentRequest,
} from '../../core/api/api-types';
import type { CatalogResource } from '../../core/admin/catalog-api';
import { formatDate, money } from '../../shared/format';
import {
  BOARD_TYPE,
  CAR_CATEGORY,
  COMPONENT_TYPE,
  ENTITY_STATUS,
  options,
  TRANSMISSION,
} from '../../shared/labels';

export interface Option {
  readonly value: string;
  readonly label: string;
}

/** Opções vindas de outro recurso do catálogo (ex.: cidades no voo). */
export interface OptionSource {
  readonly resource: CatalogResource;
  readonly params?: Readonly<Record<string, string | number>>;
  readonly label: (item: never) => string;
}

export interface FieldDef {
  readonly key: string;
  readonly label: string;
  readonly type: 'text' | 'number' | 'datetime' | 'select' | 'textarea' | 'components';
  readonly required?: boolean;
  readonly min?: number;
  readonly max?: number;
  readonly step?: number;
  readonly maxLength?: number;
  readonly hint?: string;
  readonly options?: readonly Option[];
  readonly optionsFrom?: OptionSource;
}

export interface Column<T> {
  readonly header: string;
  readonly value: (item: T) => string;
  readonly num?: boolean;
}

export interface CatalogDefinition<T extends { id: string; status: string }> {
  readonly resource: CatalogResource;
  readonly path: string;
  readonly title: string;
  readonly singular: string;
  readonly sort: string;
  readonly columns: readonly Column<T>[];
  readonly fields: readonly FieldDef[];
  readonly defaults: Readonly<Record<string, unknown>>;
  /** Valor do formulário a partir do item (edição). */
  readonly toForm: (item: T) => Record<string, unknown>;
  /** Corpo do POST/PUT a partir do formulário. */
  readonly toRequest: (form: Record<string, unknown>) => unknown;
  readonly extraParams?: Readonly<Record<string, string>>;
}

const STATUS_FIELD: FieldDef = {
  key: 'status',
  label: 'Status',
  type: 'select',
  required: true,
  options: options(ENTITY_STATUS),
};
const CURRENCY_FIELD: FieldDef = {
  key: 'currency',
  label: 'Moeda',
  type: 'text',
  maxLength: 3,
  hint: 'ISO 4217, ex.: BRL',
};

const cityOptions: OptionSource = {
  resource: 'cities',
  params: { size: 100, sort: 'name,asc' },
  label: (c: { name: string; country: string }) => `${c.name} (${c.country})`,
};

const text = (v: unknown) => (typeof v === 'string' && v.trim() !== '' ? v.trim() : null);
const num = (v: unknown) => (v === '' || v === null || v === undefined ? null : Number(v));
const status = (v: unknown) => (v === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE');
const statusLabel = (s: string) => ENTITY_STATUS[s as keyof typeof ENTITY_STATUS]?.label ?? s;
const currency = (v: unknown) => text(v)?.toUpperCase() ?? null;

export const FLIGHTS: CatalogDefinition<Flight> = {
  resource: 'flights',
  path: 'voos',
  title: 'Voos',
  singular: 'voo',
  sort: 'boardingDate,asc',
  columns: [
    { header: 'Companhia', value: (f) => f.companyName },
    { header: 'Trecho', value: (f) => `${f.boardingAirport} → ${f.returnAirport}` },
    { header: 'Ida', value: (f) => formatDate(f.boardingDate.slice(0, 10)) },
    { header: 'Volta', value: (f) => formatDate(f.returnDate.slice(0, 10)) },
    { header: 'Preço/pax', value: (f) => money(f.pricePerPassenger, f.currency), num: true },
    { header: 'Assentos', value: (f) => String(f.seatsAvailable), num: true },
    { header: 'Status', value: (f) => statusLabel(f.status) },
  ],
  fields: [
    { key: 'companyName', label: 'Companhia', type: 'text', required: true, maxLength: 120 },
    {
      key: 'boardingAirport',
      label: 'Aeroporto de embarque',
      type: 'text',
      required: true,
      maxLength: 120,
    },
    {
      key: 'returnAirport',
      label: 'Aeroporto da volta',
      type: 'text',
      required: true,
      maxLength: 120,
    },
    { key: 'boardingDate', label: 'Ida (horário local)', type: 'datetime', required: true },
    { key: 'returnDate', label: 'Volta (horário local)', type: 'datetime', required: true },
    {
      key: 'originCityId',
      label: 'Cidade de origem',
      type: 'select',
      required: true,
      optionsFrom: cityOptions,
    },
    {
      key: 'destinationCityId',
      label: 'Cidade de destino',
      type: 'select',
      required: true,
      optionsFrom: cityOptions,
    },
    {
      key: 'pricePerPassenger',
      label: 'Preço por passageiro (custo)',
      type: 'number',
      required: true,
      min: 0.01,
      step: 0.01,
    },
    CURRENCY_FIELD,
    {
      key: 'seatsAvailable',
      label: 'Assentos disponíveis',
      type: 'number',
      required: true,
      min: 0,
      max: 1000,
      step: 1,
    },
    STATUS_FIELD,
  ],
  defaults: { status: 'ACTIVE', currency: 'BRL', seatsAvailable: 0 },
  toForm: (f) => ({
    ...f,
    boardingDate: f.boardingDate.slice(0, 16),
    returnDate: f.returnDate.slice(0, 16),
  }),
  toRequest: (v) => ({
    companyName: text(v['companyName']),
    boardingAirport: text(v['boardingAirport']),
    returnAirport: text(v['returnAirport']),
    boardingDate: withSeconds(v['boardingDate']),
    returnDate: withSeconds(v['returnDate']),
    originCityId: text(v['originCityId']),
    destinationCityId: text(v['destinationCityId']),
    pricePerPassenger: num(v['pricePerPassenger']),
    currency: currency(v['currency']),
    seatsAvailable: num(v['seatsAvailable']),
    status: status(v['status']),
  }),
};

export const HOTELS: CatalogDefinition<Hotel> = {
  resource: 'hotels',
  path: 'hoteis',
  title: 'Hotéis',
  singular: 'hotel',
  sort: 'name,asc',
  columns: [
    { header: 'Nome', value: (h) => h.name },
    { header: 'Estrelas', value: (h) => '★'.repeat(h.stars), num: true },
    { header: 'Endereço', value: (h) => h.address ?? '—' },
    { header: 'Status', value: (h) => statusLabel(h.status) },
  ],
  fields: [
    { key: 'name', label: 'Nome', type: 'text', required: true, maxLength: 180 },
    {
      key: 'cityId',
      label: 'Cidade',
      type: 'select',
      required: true,
      optionsFrom: {
        ...cityOptions,
        params: { size: 100, sort: 'name,asc', cityType: 'DESTINATION' },
      },
    },
    { key: 'stars', label: 'Estrelas', type: 'number', required: true, min: 1, max: 5, step: 1 },
    { key: 'address', label: 'Endereço', type: 'text', maxLength: 300 },
    { key: 'imageUrl', label: 'URL da imagem', type: 'text', maxLength: 500 },
    { key: 'description', label: 'Descrição', type: 'textarea', maxLength: 1000 },
    STATUS_FIELD,
  ],
  defaults: { status: 'ACTIVE', stars: 3 },
  toForm: (h) => ({ ...h }),
  toRequest: (v) => ({
    name: text(v['name']),
    cityId: text(v['cityId']),
    stars: num(v['stars']),
    address: text(v['address']),
    imageUrl: text(v['imageUrl']),
    description: text(v['description']),
    status: status(v['status']),
  }),
};

const hotelOptions: OptionSource = {
  resource: 'hotels',
  params: { size: 100, sort: 'name,asc' },
  label: (h: { name: string }) => h.name,
};

export const ROOM_OFFERS: CatalogDefinition<RoomOffer> = {
  resource: 'room-offers',
  path: 'quartos',
  title: 'Quartos',
  singular: 'quarto',
  sort: 'pricePerNight,asc',
  columns: [
    { header: 'Quarto', value: (r) => r.roomType },
    { header: 'Regime', value: (r) => BOARD_TYPE[r.boardType] },
    { header: 'Hóspedes', value: (r) => String(r.maxGuests), num: true },
    { header: 'Diária (custo)', value: (r) => money(r.pricePerNight, r.currency), num: true },
    { header: 'Status', value: (r) => statusLabel(r.status) },
  ],
  fields: [
    { key: 'hotelId', label: 'Hotel', type: 'select', required: true, optionsFrom: hotelOptions },
    { key: 'roomType', label: 'Tipo de quarto', type: 'text', required: true, maxLength: 120 },
    {
      key: 'boardType',
      label: 'Regime',
      type: 'select',
      required: true,
      options: options(BOARD_TYPE),
    },
    {
      key: 'maxGuests',
      label: 'Hóspedes',
      type: 'number',
      required: true,
      min: 1,
      max: 20,
      step: 1,
    },
    {
      key: 'pricePerNight',
      label: 'Diária (custo)',
      type: 'number',
      required: true,
      min: 0.01,
      step: 0.01,
    },
    CURRENCY_FIELD,
    STATUS_FIELD,
  ],
  defaults: { status: 'ACTIVE', currency: 'BRL', boardType: 'BREAKFAST', maxGuests: 2 },
  toForm: (r) => ({ ...r }),
  toRequest: (v) => ({
    hotelId: text(v['hotelId']),
    roomType: text(v['roomType']),
    boardType: v['boardType'],
    maxGuests: num(v['maxGuests']),
    pricePerNight: num(v['pricePerNight']),
    currency: currency(v['currency']),
    status: status(v['status']),
  }),
};

export const CARS: CatalogDefinition<CarRentalOffer> = {
  resource: 'car-rental-offers',
  path: 'carros',
  title: 'Carros',
  singular: 'carro',
  sort: 'pricePerDay,asc',
  columns: [
    { header: 'Locadora', value: (c) => c.companyName },
    { header: 'Modelo', value: (c) => c.carModel },
    { header: 'Categoria', value: (c) => CAR_CATEGORY[c.carCategory] },
    { header: 'Câmbio', value: (c) => TRANSMISSION[c.transmission] },
    { header: 'Lugares', value: (c) => String(c.seats), num: true },
    { header: 'Diária (custo)', value: (c) => money(c.pricePerDay, c.currency), num: true },
    { header: 'Status', value: (c) => statusLabel(c.status) },
  ],
  fields: [
    { key: 'companyName', label: 'Locadora', type: 'text', required: true, maxLength: 120 },
    { key: 'carModel', label: 'Modelo', type: 'text', required: true, maxLength: 120 },
    {
      key: 'cityId',
      label: 'Cidade',
      type: 'select',
      required: true,
      optionsFrom: {
        ...cityOptions,
        params: { size: 100, sort: 'name,asc', cityType: 'DESTINATION' },
      },
    },
    {
      key: 'carCategory',
      label: 'Categoria',
      type: 'select',
      required: true,
      options: options(CAR_CATEGORY),
    },
    {
      key: 'transmission',
      label: 'Câmbio',
      type: 'select',
      required: true,
      options: options(TRANSMISSION),
    },
    { key: 'seats', label: 'Lugares', type: 'number', required: true, min: 1, max: 15, step: 1 },
    {
      key: 'pricePerDay',
      label: 'Diária (custo)',
      type: 'number',
      required: true,
      min: 0.01,
      step: 0.01,
    },
    CURRENCY_FIELD,
    STATUS_FIELD,
  ],
  defaults: {
    status: 'ACTIVE',
    currency: 'BRL',
    carCategory: 'COMPACT',
    transmission: 'MANUAL',
    seats: 5,
  },
  toForm: (c) => ({ ...c }),
  toRequest: (v) => ({
    companyName: text(v['companyName']),
    carModel: text(v['carModel']),
    cityId: text(v['cityId']),
    carCategory: v['carCategory'],
    transmission: v['transmission'],
    seats: num(v['seats']),
    pricePerDay: num(v['pricePerDay']),
    currency: currency(v['currency']),
    status: status(v['status']),
  }),
};

export const PACKAGES: CatalogDefinition<TravelPackage> = {
  resource: 'travel-packages',
  path: 'pacotes',
  title: 'Pacotes',
  singular: 'pacote',
  sort: 'title,asc',
  columns: [
    { header: 'Título', value: (p) => p.title },
    { header: 'Preço', value: (p) => money(p.price, p.currency), num: true },
    {
      header: 'Promoção',
      value: (p) =>
        p.pricePromotion !== null && p.pricePromotion !== undefined
          ? money(p.pricePromotion, p.currency)
          : '—',
      num: true,
    },
    { header: 'Componentes', value: (p) => String(p.components.length), num: true },
    { header: 'Status', value: (p) => statusLabel(p.status) },
  ],
  fields: [
    { key: 'title', label: 'Título', type: 'text', required: true, maxLength: 180 },
    { key: 'price', label: 'Preço de vitrine', type: 'number', required: true, min: 0, step: 0.01 },
    {
      key: 'pricePromotion',
      label: 'Preço promocional',
      type: 'number',
      min: 0,
      step: 0.01,
      hint: 'Menor que o preço. Vira desconto percentual na cotação. Vazio = sem promoção.',
    },
    CURRENCY_FIELD,
    {
      key: 'flightId',
      label: 'Voo',
      type: 'select',
      required: true,
      optionsFrom: {
        resource: 'flights',
        params: { size: 100, sort: 'boardingDate,asc' },
        label: (f: Flight) =>
          `${f.companyName} · ${f.boardingAirport} → ${f.returnAirport} · ${formatDate(f.boardingDate.slice(0, 10))}`,
      },
    },
    {
      key: 'categoryId',
      label: 'Categoria',
      type: 'select',
      required: true,
      optionsFrom: {
        resource: 'categories',
        params: { size: 100, sort: 'title,asc' },
        label: (c: { title: string }) => c.title,
      },
    },
    {
      key: 'originCityId',
      label: 'Origem',
      type: 'select',
      required: true,
      optionsFrom: cityOptions,
    },
    {
      key: 'destinationCityId',
      label: 'Destino',
      type: 'select',
      required: true,
      optionsFrom: cityOptions,
    },
    { key: 'imageUrl', label: 'URL da imagem', type: 'text', maxLength: 500 },
    STATUS_FIELD,
    {
      key: 'components',
      label: 'Hospedagem e carro do pacote',
      type: 'components',
      hint: 'Noites de hotel ou dias de carro a partir do embarque. O voo vem do campo acima.',
    },
  ],
  defaults: { status: 'ACTIVE', currency: 'BRL', components: [] },
  toForm: (p) => ({
    ...p,
    components: p.components.map((c) => ({
      type: c.type,
      offerId: c.roomOfferId ?? c.carRentalOfferId ?? '',
      quantity: c.quantity,
    })),
  }),
  toRequest: (v) => ({
    title: text(v['title']),
    price: num(v['price']),
    pricePromotion: num(v['pricePromotion']),
    currency: currency(v['currency']),
    status: status(v['status']),
    imageUrl: text(v['imageUrl']),
    flightId: text(v['flightId']),
    categoryId: text(v['categoryId']),
    originCityId: text(v['originCityId']),
    destinationCityId: text(v['destinationCityId']),
    components: ((v['components'] as ComponentRow[] | undefined) ?? []).map(toComponentRequest),
  }),
};

export interface ComponentRow {
  type: 'HOTEL_STAY' | 'CAR_RENTAL';
  offerId: string;
  quantity: number;
}

export function toComponentRequest(row: ComponentRow): TravelPackageComponentRequest {
  return {
    type: row.type,
    roomOfferId: row.type === 'HOTEL_STAY' ? row.offerId || null : null,
    carRentalOfferId: row.type === 'CAR_RENTAL' ? row.offerId || null : null,
    quantity: Number(row.quantity),
  };
}

export const COMPONENT_TYPES = options(COMPONENT_TYPE);

/** "2027-03-10T08:00" (input datetime-local) -> "2027-03-10T08:00:00" (LocalDateTime da API). */
export function withSeconds(value: unknown): string | null {
  const t = text(value);
  if (!t) return null;
  return t.length === 16 ? `${t}:00` : t;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const DEFINITIONS: Record<string, CatalogDefinition<any>> = {
  voos: FLIGHTS,
  hoteis: HOTELS,
  quartos: ROOM_OFFERS,
  carros: CARS,
  pacotes: PACKAGES,
};
