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
  | 'REMOTE_CONTENT_TOO_LARGE';
