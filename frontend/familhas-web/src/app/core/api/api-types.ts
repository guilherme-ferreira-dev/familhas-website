/**
 * Apelidos dos tipos gerados dos contratos em docs/api (npm run api:types). Nunca editar os .d.ts à mão.
 * - schema.d.ts: familhas-api.yaml (Fase 7), catálogo, busca e /api/me.
 * - checkout-schema.d.ts: checkout.yaml (Fase 6), cotações, reservas e checkout.
 */
import type { components as CheckoutComponents } from './checkout-schema';
import type { components } from './schema';

type Schemas = components['schemas'];
type Checkout = CheckoutComponents['schemas'];

export type ProblemDetail = Schemas['ProblemDetail'];
export type City = Schemas['City'];
export type CityPage = Schemas['CityPage'];
export type Category = Schemas['Category'];
export type CategoryPage = Schemas['CategoryPage'];
export type TravelPackage = Schemas['TravelPackage'];
export type Flight = Schemas['Flight'];
export type Hotel = Schemas['Hotel'];
export type RoomOffer = Schemas['RoomOffer'];
export type CarRentalOffer = Schemas['CarRentalOffer'];
export type BoardType = Schemas['BoardType'];
export type CarCategory = Schemas['CarCategory'];
export type PackageSearchResult = Schemas['PackageSearchResult'];
export type PackageSearchPage = Schemas['PackageSearchPage'];
export type FlightSearchResult = Schemas['FlightSearchResult'];
export type FlightSearchPage = Schemas['FlightSearchPage'];
export type HotelSearchResult = Schemas['HotelSearchResult'];
export type HotelSearchPage = Schemas['HotelSearchPage'];
export type CarSearchResult = Schemas['CarSearchResult'];
export type CarSearchPage = Schemas['CarSearchPage'];
export type Me = Schemas['Me'];

export type QuoteRequest = Checkout['QuoteRequest'];
export type Quote = Checkout['QuoteResponse'];
export type QuoteItem = Checkout['QuoteItem'];
export type QuoteStatus = Quote['status'];
export type Booking = Checkout['BookingResponse'];
export type BookingStatus = Checkout['BookingStatus'];
export type BookingRequest = Checkout['BookingRequest'];
export type PaymentMethod = Checkout['PaymentMethod'];
export type PaymentStatus = Checkout['PaymentStatus'];
export type CheckoutSessionRequest = Checkout['CheckoutSessionRequest'];
export type CheckoutSession = Checkout['CheckoutSessionResponse'];
/** Códigos estáveis dos erros de negócio da Fase 6: a tela decide a mensagem pelo code, nunca pelo detail. */
export type ProblemCode = Checkout['CodedProblem']['code'];

/** Página de busca: content + metadados + provedores que não responderam. */
export interface SearchPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unavailableProviders: string[];
}
