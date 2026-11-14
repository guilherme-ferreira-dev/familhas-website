import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';
import type { PaymentStreamEvent } from '../api/api-types';
import { AuthService } from '../auth/auth.service';
import { APP_CONFIG } from '../config/app-config';
import { parseSse } from './sse';

export type FeedStatus = 'connecting' | 'live' | 'offline';

const MAX_BACKOFF_MS = 30_000;

/**
 * Avisos em tempo real da planilha (GET /api/admin/payments/stream).
 *
 * EventSource não manda header Authorization, e token na URL vaza em log de proxy: por isso o stream é lido
 * com fetch + ReadableStream, com o token no header. A API fecha a conexão a cada 5 min; aqui se reconecta
 * na hora, com um token novo. Em erro de rede, tenta de novo com espera crescente (até 30 s).
 * Escopo da tela (providers do componente): a conexão fecha ao sair da página.
 */
@Injectable()
export class PaymentFeed {
  private readonly auth = inject(AuthService);
  private readonly url = `${inject(APP_CONFIG).apiBaseUrl}/api/admin/payments/stream`;
  private readonly events$ = new Subject<PaymentStreamEvent>();
  private controller: AbortController | null = null;
  private backoff = 1000;

  readonly status = signal<FeedStatus>('offline');
  readonly events = this.events$.asObservable();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  start(fetchFn: typeof fetch = (...args) => fetch(...args)): void {
    if (this.controller) return;
    this.controller = new AbortController();
    void this.loop(this.controller.signal, fetchFn);
  }

  stop(): void {
    this.controller?.abort();
    this.controller = null;
    this.status.set('offline');
  }

  private async loop(abort: AbortSignal, fetchFn: typeof fetch): Promise<void> {
    while (!abort.aborted) {
      this.status.set('connecting');
      let outcome: Outcome = 'retry';
      try {
        outcome = await this.readOnce(abort, fetchFn);
      } catch {
        // rede caiu ou a conexão foi abortada
      }
      if (abort.aborted) return;
      if (outcome === 'stop') {
        this.status.set('offline');
        return;
      }
      if (outcome === 'retry') {
        this.status.set('offline');
        await sleep(this.backoff, abort);
        this.backoff = Math.min(this.backoff * 2, MAX_BACKOFF_MS);
      }
    }
  }

  /**
   * Lê uma conexão até ela fechar. 'reconnect': fim normal (limite de 5 min da API), reconecta já com token
   * novo. 'retry': falhou, espera e tenta de novo. 'stop': 401/403 ou sem sessão, não adianta insistir.
   */
  private async readOnce(abort: AbortSignal, fetchFn: typeof fetch): Promise<Outcome> {
    const token = await this.auth.getAccessToken();
    if (!token) return 'stop';
    const response = await fetchFn(this.url, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      cache: 'no-store',
      signal: abort,
    });
    if (response.status === 401 || response.status === 403) return 'stop';
    if (!response.ok || !response.body) return 'retry';

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    let connected = false;
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      const parsed = parseSse(buffer + value);
      buffer = parsed.rest;
      for (const message of parsed.messages) {
        if (message.event === 'ready') {
          connected = true;
          this.status.set('live');
          this.backoff = 1000;
        } else if (message.event === 'payment') {
          try {
            this.events$.next(JSON.parse(message.data) as PaymentStreamEvent);
          } catch {
            // aviso malformado: a recarga periódica cobre
          }
        }
      }
    }
    return connected ? 'reconnect' : 'retry';
  }
}

type Outcome = 'reconnect' | 'retry' | 'stop';

function sleep(ms: number, abort: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (ms <= 0) return resolve();
    const timer = setTimeout(resolve, ms);
    abort.addEventListener('abort', () => {
      clearTimeout(timer);
      resolve();
    });
  });
}
