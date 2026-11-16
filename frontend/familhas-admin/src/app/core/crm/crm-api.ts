import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  CrmRole,
  InviteRequest,
  RestoreReport,
  StoredBackup,
  TeamMember,
  Brand,
  InstagramGenerateRequest,
  InstagramPost,
  InstagramPostPage,
  InstagramPostRequest,
  Itinerary,
  ItineraryPage,
  ItineraryRequest,
  PostStatus,
  ClientFlight,
  ClientFlightPage,
  ClientFlightRequest,
  CrmDashboard,
  CrmFile,
  CrmFilePage,
  FileCategory,
  FileLink,
  FlightSearchGroup,
  FlightSearchGroupRequest,
  FlightSearchOptions,
  FlightSearchRequest,
  FlightSearchResult,
  RecentFlightSearch,
  Voucher,
  VoucherDetails,
  VoucherDocument,
  VoucherPage,
  VoucherRequest,
  VoucherStatus,
  VoucherType,
} from '../api/crm-types';
import { queryParams } from '../admin/query-params';
import { APP_CONFIG } from '../config/app-config';

export interface ClientFlightFilter {
  readonly q?: string | null;
  readonly archived?: boolean;
  readonly from?: string | null;
  readonly to?: string | null;
  readonly airline?: string | null;
  readonly page?: number;
  readonly size?: number;
}

export interface VoucherFilter {
  readonly q?: string | null;
  readonly type?: VoucherType | null;
  readonly status?: VoucherStatus | null;
  readonly from?: string | null;
  readonly to?: string | null;
  readonly page?: number;
  readonly size?: number;
}

/** Cliente de `/api/crm/**` (contrato em docs/api/crm.yaml). Sem cache compartilhado: cada tela recarrega após salvar. */
@Injectable({ providedIn: 'root' })
export class CrmApi {
  private readonly http = inject(HttpClient);
  private readonly api = `${inject(APP_CONFIG).apiBaseUrl}/api/crm`;

  /** URL absoluta de um caminho devolvido pela API (ex.: link assinado de arquivo). */
  absolute(path: string): string {
    return `${this.api.slice(0, -'/api/crm'.length)}${path}`;
  }

  dashboard(): Observable<CrmDashboard> {
    return this.http.get<CrmDashboard>(`${this.api}/dashboard`);
  }

  // Voos Clientes
  clientFlights(filter: ClientFlightFilter): Observable<ClientFlightPage> {
    return this.http.get<ClientFlightPage>(`${this.api}/client-flights`, {
      params: queryParams({ ...filter }),
    });
  }

  clientFlight(id: string): Observable<ClientFlight> {
    return this.http.get<ClientFlight>(`${this.api}/client-flights/${encodeURIComponent(id)}`);
  }

  createClientFlight(body: ClientFlightRequest): Observable<ClientFlight> {
    return this.http.post<ClientFlight>(`${this.api}/client-flights`, body);
  }

  updateClientFlight(id: string, body: ClientFlightRequest): Observable<ClientFlight> {
    return this.http.put<ClientFlight>(
      `${this.api}/client-flights/${encodeURIComponent(id)}`,
      body,
    );
  }

  setArchived(id: string, archived: boolean): Observable<ClientFlight> {
    const action = archived ? 'archive' : 'unarchive';
    return this.http.post<ClientFlight>(
      `${this.api}/client-flights/${encodeURIComponent(id)}/${action}`,
      null,
    );
  }

  deleteClientFlight(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/client-flights/${encodeURIComponent(id)}`);
  }

  attachToClientFlight(id: string, file: File): Observable<ClientFlight> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ClientFlight>(
      `${this.api}/client-flights/${encodeURIComponent(id)}/attachments`,
      form,
    );
  }

  detachFromClientFlight(id: string, fileId: string): Observable<ClientFlight> {
    return this.http.delete<ClientFlight>(
      `${this.api}/client-flights/${encodeURIComponent(id)}/attachments/${encodeURIComponent(fileId)}`,
    );
  }

  // Arquivos
  files(category: FileCategory, name: string | null, page = 0): Observable<CrmFilePage> {
    return this.http.get<CrmFilePage>(`${this.api}/files`, {
      params: queryParams({ category, name, page, size: 20 }),
    });
  }

  uploadFile(category: FileCategory, file: File): Observable<CrmFile> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<CrmFile>(`${this.api}/files`, form, {
      params: queryParams({ category }),
    });
  }

  deleteFile(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/files/${encodeURIComponent(id)}`);
  }

  /** Link assinado de curta duração (auditado). */
  fileLink(id: string): Observable<FileLink> {
    return this.http.post<FileLink>(`${this.api}/files/${encodeURIComponent(id)}/link`, null);
  }

  // Pesquisar Voos
  flightSearchOptions(): Observable<FlightSearchOptions> {
    return this.http.get<FlightSearchOptions>(`${this.api}/flight-search/options`);
  }

  searchFlights(request: FlightSearchRequest): Observable<FlightSearchResult> {
    return this.http.post<FlightSearchResult>(`${this.api}/flight-search`, request);
  }

  recentFlightSearches(): Observable<RecentFlightSearch[]> {
    return this.http.get<RecentFlightSearch[]>(`${this.api}/flight-search/recent`);
  }

  flightSearchGroups(): Observable<FlightSearchGroup[]> {
    return this.http.get<FlightSearchGroup[]>(`${this.api}/flight-search/groups`);
  }

  createFlightSearchGroup(body: FlightSearchGroupRequest): Observable<FlightSearchGroup> {
    return this.http.post<FlightSearchGroup>(`${this.api}/flight-search/groups`, body);
  }

  deleteFlightSearchGroup(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/flight-search/groups/${encodeURIComponent(id)}`);
  }

  // Vouchers
  vouchers(filter: VoucherFilter): Observable<VoucherPage> {
    return this.http.get<VoucherPage>(`${this.api}/vouchers`, {
      params: queryParams({ ...filter }),
    });
  }

  voucher(id: string): Observable<Voucher> {
    return this.http.get<Voucher>(`${this.api}/vouchers/${encodeURIComponent(id)}`);
  }

  issueVoucher(body: VoucherRequest): Observable<Voucher> {
    return this.http.post<Voucher>(`${this.api}/vouchers`, body);
  }

  updateVoucher(id: string, body: VoucherRequest): Observable<Voucher> {
    return this.http.put<Voucher>(`${this.api}/vouchers/${encodeURIComponent(id)}`, body);
  }

  cancelVoucher(id: string): Observable<Voucher> {
    return this.http.post<Voucher>(`${this.api}/vouchers/${encodeURIComponent(id)}/cancel`, null);
  }

  /** Documento A4 com CPF inteiro: cada chamada fica na auditoria. */
  voucherDocument(id: string): Observable<VoucherDocument> {
    return this.http.get<VoucherDocument>(
      `${this.api}/vouchers/${encodeURIComponent(id)}/document`,
    );
  }

  extractVoucher(type: VoucherType, fileId: string): Observable<VoucherDetails> {
    return this.http.post<VoucherDetails>(`${this.api}/vouchers/extract`, { type, fileId });
  }

  // Identidade visual
  brand(): Observable<Brand> {
    return this.http.get<Brand>(`${this.api}/brand`);
  }

  replaceLogo(file: File): Observable<Brand> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<Brand>(`${this.api}/brand/logo`, form);
  }

  // Criar posts Insta
  instagramPosts(status: readonly PostStatus[], page = 0): Observable<InstagramPostPage> {
    return this.http.get<InstagramPostPage>(`${this.api}/instagram-posts`, {
      params: queryParams({ status, page, size: 20 }),
    });
  }

  instagramPost(id: string): Observable<InstagramPost> {
    return this.http.get<InstagramPost>(`${this.api}/instagram-posts/${encodeURIComponent(id)}`);
  }

  createInstagramPost(body: InstagramPostRequest): Observable<InstagramPost> {
    return this.http.post<InstagramPost>(`${this.api}/instagram-posts`, body);
  }

  updateInstagramPost(id: string, body: InstagramPostRequest): Observable<InstagramPost> {
    return this.http.put<InstagramPost>(
      `${this.api}/instagram-posts/${encodeURIComponent(id)}`,
      body,
    );
  }

  instagramPostAction(id: string, action: 'ready' | 'publish'): Observable<InstagramPost> {
    return this.http.post<InstagramPost>(
      `${this.api}/instagram-posts/${encodeURIComponent(id)}/${action}`,
      null,
    );
  }

  resolveInstagramPost(
    id: string,
    published: boolean,
    permalink: string | null,
  ): Observable<InstagramPost> {
    return this.http.post<InstagramPost>(
      `${this.api}/instagram-posts/${encodeURIComponent(id)}/resolve`,
      { published, permalink },
    );
  }

  deleteInstagramPost(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/instagram-posts/${encodeURIComponent(id)}`);
  }

  generateInstagramPost(body: InstagramGenerateRequest): Observable<InstagramPostRequest> {
    return this.http.post<InstagramPostRequest>(`${this.api}/instagram-posts/generate`, body);
  }

  // IA dos posts de cotação e cruzeiros
  extractQuote(
    kind: 'PACKAGE' | 'CRUISE',
    fileId: string | null,
    url: string | null,
  ): Observable<unknown> {
    return this.http.post(`${this.api}/marketing/extract-quote`, { kind, fileId, url });
  }

  generateImage(subject: 'CITY' | 'SHIP', name: string): Observable<unknown> {
    return this.http.post(`${this.api}/marketing/generate-image`, { subject, name });
  }

  // Gerar Roteiro
  itineraries(q: string | null, page = 0): Observable<ItineraryPage> {
    return this.http.get<ItineraryPage>(`${this.api}/itineraries`, {
      params: queryParams({ q, page, size: 20 }),
    });
  }

  itinerary(id: string): Observable<Itinerary> {
    return this.http.get<Itinerary>(`${this.api}/itineraries/${encodeURIComponent(id)}`);
  }

  createItinerary(body: ItineraryRequest): Observable<Itinerary> {
    return this.http.post<Itinerary>(`${this.api}/itineraries`, body);
  }

  updateItinerary(id: string, body: ItineraryRequest): Observable<Itinerary> {
    return this.http.put<Itinerary>(`${this.api}/itineraries/${encodeURIComponent(id)}`, body);
  }

  deleteItinerary(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/itineraries/${encodeURIComponent(id)}`);
  }

  generateItinerary(city: string, days: number): Observable<unknown> {
    return this.http.post(`${this.api}/itineraries/generate`, { city, days });
  }

  // Equipe
  team(): Observable<TeamMember[]> {
    return this.http.get<TeamMember[]>(`${this.api}/team`);
  }

  invite(body: InviteRequest): Observable<TeamMember> {
    return this.http.post<TeamMember>(`${this.api}/team`, body);
  }

  changeRole(id: string, role: CrmRole): Observable<TeamMember> {
    return this.http.put<TeamMember>(`${this.api}/team/${encodeURIComponent(id)}/role`, { role });
  }

  memberAction(id: string, action: 'disable' | 'enable' | 'resend'): Observable<TeamMember> {
    return this.http.post<TeamMember>(`${this.api}/team/${encodeURIComponent(id)}/${action}`, null);
  }

  // Backup
  storedBackups(): Observable<StoredBackup[]> {
    return this.http.get<StoredBackup[]>(`${this.api}/backups`);
  }

  exportBackup(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/backups/export`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  downloadStoredBackup(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/backups/${encodeURIComponent(id)}/download`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  emailBackup(to: string | null): Observable<StoredBackup> {
    return this.http.post<StoredBackup>(`${this.api}/backups/email`, { to });
  }

  restoreBackup(file: File, apply: boolean): Observable<RestoreReport> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<RestoreReport>(`${this.api}/backups/restore`, form, {
      params: queryParams({ apply }),
    });
  }
}
