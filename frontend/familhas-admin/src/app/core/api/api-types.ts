import type { components } from './schema';

/** Atalhos para os tipos gerados de docs/api/admin.yaml (npm run api:types). */
type Schemas = components['schemas'];

export type PageMeta = Schemas['PageMeta'];
export type Page<T> = PageMeta & { content: T[] };

export type EntityStatus = Schemas['EntityStatus'];
export type CityType = Schemas['CityType'];
export type BoardType = Schemas['BoardType'];
export type CarCategory = Schemas['CarCategory'];
export type Transmission = Schemas['Transmission'];
export type PackageComponentType = Schemas['PackageComponentType'];
export type PriceItemType = Schemas['PriceItemType'];
export type BookingStatus = Schemas['BookingStatus'];
export type PaymentStatus = Schemas['PaymentStatus'];
export type PaymentMethod = Schemas['PaymentMethod'];
export type QuoteStatus = Schemas['QuoteStatus'];
export type QuoteOrigin = Schemas['QuoteOrigin'];
export type ClientSegment = Schemas['ClientSegment'];
export type AuditAction = Schemas['AuditAction'];

export type Client = Schemas['Client'];
export type ClientRequest = Schemas['ClientRequest'];
export type ClientRef = Schemas['ClientRef'];
export type ClientSegmentCounts = Schemas['ClientSegmentCounts'];
export type AdminBookingSummary = Schemas['AdminBookingSummary'];
export type AdminBookingDetail = Schemas['AdminBookingDetail'];
export type PricedItem = Schemas['PricedItem'];
export type AdminPayment = Schemas['AdminPayment'];
export type PaymentStreamEvent = Schemas['PaymentStreamEvent'];
export type Dashboard = Schemas['Dashboard'];
export type ManualQuoteRequest = Schemas['ManualQuoteRequest'];
export type AdminQuoteSummary = Schemas['AdminQuoteSummary'];
export type AdminQuoteDetail = Schemas['AdminQuoteDetail'];
export type PricingSettings = Schemas['PricingSettings'];
export type PricingSettingsRequest = Schemas['PricingSettingsRequest'];
export type AuditLog = Schemas['AuditLog'];

export type City = Schemas['City'];
export type Category = Schemas['Category'];
export type Flight = Schemas['Flight'];
export type FlightRequest = Schemas['FlightRequest'];
export type Hotel = Schemas['Hotel'];
export type HotelRequest = Schemas['HotelRequest'];
export type RoomOffer = Schemas['RoomOffer'];
export type RoomOfferRequest = Schemas['RoomOfferRequest'];
export type CarRentalOffer = Schemas['CarRentalOffer'];
export type CarRentalOfferRequest = Schemas['CarRentalOfferRequest'];
export type TravelPackage = Schemas['TravelPackage'];
export type TravelPackageRequest = Schemas['TravelPackageRequest'];
export type TravelPackageComponentRequest = Schemas['TravelPackageComponentRequest'];

/** Códigos estáveis de regra de negócio que o Admin trata (Problem Details `code`). */
export type ProblemCode = 'QUOTE_BELOW_COST' | 'CLIENT_INACTIVE';
