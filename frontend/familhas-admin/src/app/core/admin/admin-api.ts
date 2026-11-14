import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  AdminBookingDetail,
  AdminBookingSummary,
  AdminPayment,
  AdminQuoteDetail,
  AdminQuoteSummary,
  AuditAction,
  AuditLog,
  BookingStatus,
  Client,
  ClientRequest,
  ClientSegment,
  ClientSegmentCounts,
  Dashboard,
  EntityStatus,
  ManualQuoteRequest,
  Page,
  PaymentMethod,
  PaymentStatus,
  PricingSettings,
  PricingSettingsRequest,
  QuoteOrigin,
  QuoteStatus,
} from '../api/api-types';
import { APP_CONFIG } from '../config/app-config';
import { QueryValue, queryParams } from './query-params';

export interface PageRequest {
  readonly page?: number;
  readonly size?: number;
  readonly sort?: string;
}

export interface ClientFilter extends PageRequest {
  readonly segment?: ClientSegment | null;
  readonly status?: EntityStatus | null;
  readonly q?: string | null;
}

export interface BookingFilter extends PageRequest {
  readonly status?: BookingStatus | null;
  readonly clientId?: string | null;
  readonly from?: string | null;
  readonly to?: string | null;
}

export interface PaymentFilter {
  readonly from?: string | null;
  readonly to?: string | null;
  readonly dateField?: 'CREATED' | 'PAID' | null;
  readonly status?: readonly PaymentStatus[];
  readonly method?: PaymentMethod | null;
  readonly installments?: number | null;
  readonly clientId?: string | null;
  readonly bookingId?: string | null;
}

export interface QuoteFilter extends PageRequest {
  readonly origin?: QuoteOrigin | null;
  readonly status?: QuoteStatus | null;
  readonly clientId?: string | null;
}

export interface AuditFilter extends PageRequest {
  readonly actor?: string | null;
  readonly resource?: string | null;
  readonly action?: AuditAction | null;
  readonly from?: string | null;
  readonly to?: string | null;
}

export type ExportFormat = 'xlsx' | 'csv';

/** Chamadas a /api/admin (docs/api/admin.yaml), exceto o catálogo, que fica em CatalogApi. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiBaseUrl}/api/admin`;

  dashboard(from?: string | null, to?: string | null): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/dashboard`, {
      params: queryParams({ from, to }),
    });
  }

  clients(filter: ClientFilter): Observable<Page<Client>> {
    return this.http.get<Page<Client>>(`${this.base}/clients`, { params: params(filter) });
  }

  clientSegments(): Observable<ClientSegmentCounts> {
    return this.http.get<ClientSegmentCounts>(`${this.base}/clients/segments`);
  }

  client(id: string): Observable<Client> {
    return this.http.get<Client>(`${this.base}/clients/${encodeURIComponent(id)}`);
  }

  updateClient(id: string, request: ClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.base}/clients/${encodeURIComponent(id)}`, request);
  }

  bookings(filter: BookingFilter): Observable<Page<AdminBookingSummary>> {
    return this.http.get<Page<AdminBookingSummary>>(`${this.base}/bookings`, {
      params: params(filter),
    });
  }

  booking(id: string): Observable<AdminBookingDetail> {
    return this.http.get<AdminBookingDetail>(`${this.base}/bookings/${encodeURIComponent(id)}`);
  }

  payments(filter: PaymentFilter, page: PageRequest): Observable<Page<AdminPayment>> {
    return this.http.get<Page<AdminPayment>>(`${this.base}/payments`, {
      params: params({ ...filter, ...page }),
    });
  }

  payment(id: string): Observable<AdminPayment> {
    return this.http.get<AdminPayment>(`${this.base}/payments/${encodeURIComponent(id)}`);
  }

  /** Arquivo da planilha filtrada. O nome vem do Content-Disposition da API. */
  exportPayments(filter: PaymentFilter, format: ExportFormat): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/payments/export`, {
      params: params({ ...filter, format }),
      observe: 'response',
      responseType: 'blob',
    });
  }

  quotes(filter: QuoteFilter): Observable<Page<AdminQuoteSummary>> {
    return this.http.get<Page<AdminQuoteSummary>>(`${this.base}/quotes`, {
      params: params(filter),
    });
  }

  quote(id: string): Observable<AdminQuoteDetail> {
    return this.http.get<AdminQuoteDetail>(`${this.base}/quotes/${encodeURIComponent(id)}`);
  }

  createQuote(request: ManualQuoteRequest): Observable<AdminQuoteDetail> {
    return this.http.post<AdminQuoteDetail>(`${this.base}/quotes`, request);
  }

  pricingSettings(): Observable<PricingSettings> {
    return this.http.get<PricingSettings>(`${this.base}/pricing-settings`);
  }

  updatePricingSettings(request: PricingSettingsRequest): Observable<PricingSettings> {
    return this.http.put<PricingSettings>(`${this.base}/pricing-settings`, request);
  }

  auditLogs(filter: AuditFilter): Observable<Page<AuditLog>> {
    return this.http.get<Page<AuditLog>>(`${this.base}/audit-logs`, { params: params(filter) });
  }
}

function params(values: object) {
  return queryParams(values as Record<string, QueryValue>);
}
