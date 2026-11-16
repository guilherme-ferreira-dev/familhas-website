import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
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
}
