import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { ClientFlight, CrmFile } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { fileSize, formatLocalDateTime } from '../../shared/crm-format';
import { FileViewer } from '../../shared/file-viewer';
import { formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';

/** Voo de cliente: dados, passageiros (CPF mascarado), anexos, arquivar e excluir. */
@Component({
  selector: 'app-client-flight-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LoadError, FileViewer],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/voos-clientes">← Voos Clientes</a></p>
      @if (flight.error()) {
        <app-load-error [error]="flight.error()" (retry)="flight.reload()" />
      }
      @if (flight.value(); as f) {
        <div class="page-header">
          <div>
            <h1 id="titulo">{{ f.origin }} → {{ f.destination }}</h1>
            <p>
              {{ f.airline }} · ida {{ when(f.outboundAt) }}
              @if (f.archived) {
                · <strong>arquivado</strong>
              }
            </p>
          </div>
          <div class="actions">
            @if (session.can('crm:voos-clientes:editar')) {
              <a class="btn" [routerLink]="['/voos-clientes', f.id, 'editar']">Editar</a>
              <button type="button" class="btn" [disabled]="busy()" (click)="toggleArchive(f)">
                {{ f.archived ? 'Voltar para ativos' : 'Arquivar' }}
              </button>
            }
            @if (session.can('crm:voos-clientes:excluir')) {
              <button type="button" class="btn btn--danger" [disabled]="busy()" (click)="remove(f)">
                Excluir
              </button>
            }
          </div>
        </div>

        <div class="grid">
          <section class="card" aria-labelledby="dados">
            <h2 id="dados">Voo</h2>
            <dl class="details">
              <dt>Localizador</dt>
              <dd class="mono">{{ f.locator ?? '—' }}</dd>
              <dt>Ida</dt>
              <dd>{{ when(f.outboundAt) }} · voo {{ f.outboundFlightNumber ?? '—' }}</dd>
              <dt>Volta</dt>
              <dd>
                @if (f.returnAt) {
                  {{ when(f.returnAt) }} · voo {{ f.returnFlightNumber ?? '—' }}
                } @else {
                  só ida
                }
              </dd>
              <dt>Observações</dt>
              <dd class="pre">{{ f.notes ?? '—' }}</dd>
              <dt>Cadastro</dt>
              <dd>{{ f.createdBy ?? '—' }} · {{ dateTime(f.createdAt) }}</dd>
            </dl>
          </section>

          <section class="card" aria-labelledby="passageiros">
            <h2 id="passageiros">Passageiros ({{ f.travelers.length }})</h2>
            <ul class="plain">
              @for (t of f.travelers; track t.id) {
                <li>
                  <strong>{{ t.fullName }}</strong>
                  <span class="muted">
                    CPF {{ t.cpfMasked ?? 'não informado' }} · {{ t.phone ?? 'sem telefone' }}
                  </span>
                </li>
              }
            </ul>
          </section>
        </div>

        <section class="card" aria-labelledby="anexos">
          <h2 id="anexos">Anexos ({{ f.attachments.length }})</h2>
          <ul class="plain">
            @for (a of f.attachments; track a.id) {
              <li class="attachment">
                <button type="button" class="link" (click)="viewing.set(a)">{{ a.name }}</button>
                <span class="muted">{{ size(a) }} · {{ a.uploadedBy }}</span>
                @if (session.can('crm:voos-clientes:excluir')) {
                  <button
                    type="button"
                    class="btn btn--small"
                    [disabled]="busy()"
                    (click)="detach(f, a)"
                  >
                    Remover
                  </button>
                }
              </li>
            } @empty {
              <li class="muted">Nenhum anexo.</li>
            }
          </ul>
          @if (session.can('crm:voos-clientes:editar') && session.can('crm:voos-clientes:criar')) {
            <div class="field">
              <label for="anexo">Anexar bilhete ou documento (PDF, JPG ou PNG, até 15 MB)</label>
              <input
                id="anexo"
                type="file"
                accept="application/pdf,image/jpeg,image/png"
                [disabled]="busy()"
                (change)="attach(f, $event)"
              />
            </div>
          }
        </section>

        @if (viewing(); as file) {
          <app-file-viewer [file]="file" (closed)="viewing.set(null)" />
        }
      }
    </section>
  `,
  styles: `
    .grid {
      display: grid;
      gap: var(--space-3);
      margin-bottom: var(--space-3);
    }
    @media (min-width: 60rem) {
      .grid {
        grid-template-columns: 1fr 1fr;
      }
    }
    h2 {
      margin: 0 0 var(--space-2);
      font-size: 1.125rem;
    }
    .plain {
      list-style: none;
      margin: 0 0 var(--space-3);
      padding: 0;
      display: grid;
      gap: var(--space-2);
    }
    .plain li {
      display: grid;
    }
    .attachment {
      grid-template-columns: 1fr auto;
      align-items: center;
    }
    .link {
      background: none;
      border: 0;
      padding: 0;
      color: var(--color-primary);
      text-decoration: underline;
      cursor: pointer;
      font: inherit;
      text-align: start;
    }
    .pre {
      white-space: pre-wrap;
    }
  `,
})
export class ClientFlightDetailPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  readonly id = input.required<string>();
  protected readonly busy = signal(false);
  protected readonly viewing = signal<CrmFile | null>(null);
  protected readonly flight = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.clientFlight(params),
  });

  constructor() {
    void this.session.ready();
  }

  protected when(value: string): string {
    return formatLocalDateTime(value);
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected size(file: CrmFile): string {
    return fileSize(file.sizeBytes);
  }

  protected toggleArchive(f: ClientFlight): Promise<void> {
    return this.run(async () => {
      this.flight.set(await firstValueFrom(this.api.setArchived(f.id, !f.archived)));
      this.notices.info(f.archived ? 'Voo de volta para os ativos.' : 'Voo arquivado.');
    });
  }

  protected remove(f: ClientFlight): Promise<void> {
    if (!confirm('Excluir este voo e os anexos? Não dá para desfazer.')) return Promise.resolve();
    return this.run(async () => {
      await firstValueFrom(this.api.deleteClientFlight(f.id));
      this.notices.info('Voo excluído.');
      await this.router.navigateByUrl('/voos-clientes');
    });
  }

  protected attach(f: ClientFlight, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return Promise.resolve();
    return this.run(async () => {
      this.flight.set(await firstValueFrom(this.api.attachToClientFlight(f.id, file)));
      this.notices.info('Anexo enviado.');
    }).finally(() => (input.value = ''));
  }

  protected detach(f: ClientFlight, file: CrmFile): Promise<void> {
    if (!confirm(`Remover o anexo ${file.name}?`)) return Promise.resolve();
    return this.run(async () => {
      this.flight.set(await firstValueFrom(this.api.detachFromClientFlight(f.id, file.id)));
      if (this.viewing()?.id === file.id) this.viewing.set(null);
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      this.notices.error(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
