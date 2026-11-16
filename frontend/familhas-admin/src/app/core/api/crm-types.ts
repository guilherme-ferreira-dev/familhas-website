import type { components } from './crm-schema';

/** Atalhos para os tipos gerados de docs/api/crm.yaml (npm run api:types). */
type Schemas = components['schemas'];

export type CrmRole = Schemas['CrmRole'];
export type CrmMe = Schemas['CrmMe'];
export type Company = Schemas['Company'];
export type IntegrationKey = Schemas['IntegrationKey'];
export type IntegrationStatus = Schemas['IntegrationStatus'];
export type CrmSettings = Schemas['CrmSettings'];
export type FileCategory = Schemas['FileCategory'];
export type CrmFile = Schemas['CrmFile'];
export type CrmFilePage = Schemas['CrmFilePage'];
export type FileLink = Schemas['FileLink'];

/** Códigos de erro do CRM com mensagem própria (Problem Details `code`). */
export type CrmProblemCode =
  | 'INTEGRATION_NOT_CONFIGURED'
  | 'FILE_EMPTY'
  | 'FILE_TOO_LARGE'
  | 'FILE_TYPE_NOT_ALLOWED'
  | 'FILE_TYPE_MISMATCH'
  | 'FILE_NOT_FOUND'
  | 'FILE_LINK_INVALID'
  | 'URL_NOT_ALLOWED'
  | 'REMOTE_UNAVAILABLE'
  | 'REMOTE_CONTENT_TOO_LARGE'
  | 'VERSION_CONFLICT'
  | 'CPF_INVALID'
  | 'RETURN_BEFORE_OUTBOUND'
  | 'TOO_MANY_ATTACHMENTS'
  | 'CLIENT_FLIGHT_NOT_FOUND'
  | 'VOUCHER_FIELDS_MISSING'
  | 'VOUCHER_CANCELLED'
  | 'VOUCHER_NOT_FOUND'
  | 'FILE_CATEGORY_MISMATCH'
  | 'INVALID_DATES'
  | 'FLIGHT_SEARCH_TOO_BROAD'
  | 'FLIGHT_SEARCH_QUOTA'
  | 'FLIGHT_SEARCH_FAILED'
  | 'ORIGIN_NOT_ALLOWED'
  | 'SAME_ORIGIN_DESTINATION'
  | 'DUPLICATED_DESTINATION'
  | 'GROUP_NAME_TAKEN';

export type CrmDashboard = Schemas['CrmDashboard'];
export type MonthCount = Schemas['MonthCount'];
export type ClientFlightSummary = Schemas['ClientFlightSummary'];
export type ClientFlightPage = Schemas['ClientFlightPage'];
export type ClientFlight = Schemas['ClientFlight'];
export type ClientFlightRequest = Schemas['ClientFlightRequest'];
export type ClientFlightTravelerRequest = Schemas['ClientFlightTravelerRequest'];
export type FlightSearchMode = Schemas['FlightSearchMode'];
export type FlightSearchRequest = Schemas['FlightSearchRequest'];
export type FlightSearchResult = Schemas['FlightSearchResult'];
export type FlightOffer = Schemas['FlightOffer'];
export type RecentFlightSearch = Schemas['RecentFlightSearch'];
export type FlightSearchOptions = Schemas['FlightSearchOptions'];
export type FlightSearchGroup = Schemas['FlightSearchGroup'];
export type FlightSearchGroupRequest = Schemas['FlightSearchGroupRequest'];
export type VoucherType = Schemas['VoucherType'];
export type VoucherStatus = Schemas['VoucherStatus'];
export type VoucherDetails = Schemas['VoucherDetails'];
export type VoucherRequest = Schemas['VoucherRequest'];
export type Voucher = Schemas['Voucher'];
export type VoucherSummary = Schemas['VoucherSummary'];
export type VoucherPage = Schemas['VoucherPage'];
export type VoucherDocument = Schemas['VoucherDocument'];
