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
  | 'GROUP_NAME_TAKEN'
  | 'INVALID_POST_SHAPE'
  | 'TOO_MANY_HASHTAGS'
  | 'DUPLICATED_MEDIA'
  | 'CAPTION_REQUIRED'
  | 'POST_LOCKED'
  | 'POST_NOT_UNCERTAIN'
  | 'PUBLISH_NOT_ALLOWED'
  | 'IMAGE_NOT_JPEG'
  | 'CAROUSEL_TOO_LARGE'
  | 'CAPTION_TOO_LONG'
  | 'SOURCE_REQUIRED'
  | 'DAY_OUT_OF_RANGE'
  | 'MEMBER_EXISTS'
  | 'MEMBER_NOT_FOUND'
  | 'MEMBER_DISABLED'
  | 'SELF_CHANGE_NOT_ALLOWED'
  | 'LAST_ADMIN'
  | 'INCIDENT_NOT_FOUND'
  | 'CONTAINED_AT_REQUIRED'
  | 'CLOSED_AT_REQUIRED'
  | 'INCIDENT_DATES_INVALID'
  | 'INVITE_EMAIL_FAILED'
  | 'TEAM_DIRECTORY_FAILED'
  | 'BACKUP_INVALID'
  | 'BACKUP_VERSION'
  | 'BACKUP_CHECKSUM'
  | 'BACKUP_TOO_LARGE'
  | 'BACKUP_NOT_FOUND'
  | 'BACKUP_KEY_NOT_CONFIGURED'
  | 'BACKUP_EMAIL_FAILED';

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

export type Brand = Schemas['Brand'];
export type PostFormat = Schemas['PostFormat'];
export type PostOrigin = Schemas['PostOrigin'];
export type PostStatus = Schemas['PostStatus'];
export type InstagramSlideRequest = Schemas['InstagramSlideRequest'];
export type InstagramPostRequest = Schemas['InstagramPostRequest'];
export type InstagramPost = Schemas['InstagramPost'];
export type InstagramPostSummary = Schemas['InstagramPostSummary'];
export type InstagramPostPage = Schemas['InstagramPostPage'];
export type InstagramGenerateRequest = Schemas['InstagramGenerateRequest'];
export type ItinerarySections = Schemas['ItinerarySections'];
export type ItineraryDay = Schemas['ItineraryDay'];
export type ItineraryRequest = Schemas['ItineraryRequest'];
export type Itinerary = Schemas['Itinerary'];
export type ItinerarySummary = Schemas['ItinerarySummary'];
export type ItineraryPage = Schemas['ItineraryPage'];

export type TeamMember = Schemas['TeamMember'];
export type InviteRequest = Schemas['InviteRequest'];
export type StoredBackup = Schemas['StoredBackup'];
export type RestoreReport = Schemas['RestoreReport'];
export type SubjectRequest = Schemas['SubjectRequest'];
export type SubjectReport = Schemas['SubjectReport'];
export type AnonymizationReport = Schemas['AnonymizationReport'];
export type IncidentRequest = Schemas['IncidentRequest'];
export type Incident = Schemas['Incident'];
