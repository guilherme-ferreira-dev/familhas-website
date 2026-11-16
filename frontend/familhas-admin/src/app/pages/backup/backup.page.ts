import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import type { RestoreReport } from '../../core/api/crm-types';
import { saveDownload } from '../../core/admin/download';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { fileSize } from '../../shared/crm-format';
import { formatDateTime } from '../../shared/format';

/**
 * Backup e restauração (JSON: uma tela só, um arquivo com as tabelas de negócio; os módulos não têm botão de backup).
 * Restaurar mostra a prévia antes e nunca sobrescreve o que foi editado depois. Documentos e mídias ficam fora.
 */
@Component({
  selector: 'app-backup-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Backup e restauração</h1>
          <p>
            Um arquivo com voos de clientes, pesquisas de voos, vouchers e roteiros. Documentos,
            imagens e posts do Instagram ficam fora. O arquivo tem CPFs: guarde em local seguro.
          </p>
        </div>
      </div>

      @if (session.can('crm:backup:exportar')) {
        <div class="grid">
          <section class="card" aria-labelledby="baixar">
            <h2 id="baixar">Baixar agora</h2>
            <p class="muted">
              Gera o arquivo e baixa no seu computador. Nada fica guardado no servidor.
            </p>
            <button
              type="button"
              class="btn btn--primary"
              [disabled]="busy()"
              (click)="exportNow()"
            >
              Baixar backup
            </button>
          </section>
          <section class="card" aria-labelledby="email">
            <h2 id="email">Enviar por e-mail</h2>
            <p class="muted">
              O backup fica guardado criptografado por 7 dias. O e-mail leva só o link para baixar
              aqui, com login.
            </p>
            <div class="field">
              <label for="para">Destino</label>
              <input id="para" type="email" [(ngModel)]="to" maxlength="180" />
            </div>
            <button
              type="button"
              class="btn"
              [disabled]="busy() || !emailConfigured()"
              (click)="email()"
            >
              Guardar e enviar
            </button>
            @if (!emailConfigured()) {
              <small class="muted">Envio de e-mail não configurado no servidor.</small>
            }
          </section>
        </div>
      }

      <section class="card" aria-labelledby="guardados">
        <h2 id="guardados">Backups guardados</h2>
        <ul class="plain">
          @for (b of stored.value() ?? []; track b.id) {
            <li [class.highlight]="b.id === baixar()">
              <span
                >{{ dateTime(b.createdAt) }} · {{ b.recordCount }} registros ·
                {{ size(b.sizeBytes) }}</span
              >
              <span class="muted"
                >por {{ b.createdBy }}{{ b.emailedTo ? ' · enviado para ' + b.emailedTo : '' }} ·
                vence {{ dateTime(b.expiresAt) }}</span
              >
              @if (session.can('crm:backup:exportar')) {
                <button
                  type="button"
                  class="btn btn--small"
                  [disabled]="busy()"
                  (click)="downloadStored(b.id)"
                >
                  Baixar
                </button>
              }
            </li>
          } @empty {
            <li class="muted">Nenhum backup guardado.</li>
          }
        </ul>
      </section>

      @if (session.can('crm:backup:restaurar')) {
        <section class="card" aria-labelledby="restaurar">
          <h2 id="restaurar">Restaurar</h2>
          <p class="muted">
            Só volta o que foi apagado. O que existe hoje fica como está, inclusive o que foi
            editado depois do backup.
          </p>
          <div class="field">
            <label for="arquivo">Arquivo de backup (.json)</label>
            <input
              id="arquivo"
              type="file"
              accept="application/json,.json"
              [disabled]="busy()"
              (change)="pick($event)"
            />
          </div>
          @if (report(); as r) {
            <h3>{{ r.applied ? 'Resultado' : 'Prévia (nada foi gravado ainda)' }}</h3>
            <p class="muted">
              Backup de {{ dateTime(r.backupCreatedAt ?? '') }} por {{ r.backupCreatedBy ?? '—' }}
            </p>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Tabela</th>
                    <th scope="col" class="num">No backup</th>
                    <th scope="col" class="num">{{ r.applied ? 'Restaurados' : 'Vão voltar' }}</th>
                    <th scope="col" class="num">Mantidos</th>
                    <th scope="col" class="num">Conflitos</th>
                  </tr>
                </thead>
                <tbody>
                  @for (t of r.tables; track t.table) {
                    <tr>
                      <td>{{ t.table }}</td>
                      <td class="num">{{ t.inBackup }}</td>
                      <td class="num">{{ t.restored }}</td>
                      <td class="num">{{ t.kept }}</td>
                      <td class="num">{{ t.conflicts }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (!r.applied) {
              <button
                type="button"
                class="btn btn--danger"
                [disabled]="busy() || toRestore() === 0"
                (click)="apply()"
              >
                Restaurar {{ toRestore() }} registros
              </button>
            }
          }
        </section>
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
    .card {
      display: grid;
      gap: var(--space-2);
      margin-bottom: var(--space-3);
    }
    h2,
    h3 {
      margin: 0;
      font-size: 1.125rem;
    }
    .plain {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--space-2);
    }
    .plain li {
      display: grid;
      gap: var(--space-1);
      justify-items: start;
    }
    .highlight {
      outline: 2px solid var(--color-yellow);
      outline-offset: 4px;
      border-radius: var(--radius-sm);
    }
  `,
})
export class BackupPage {
  private readonly api = inject(CrmApi);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  /** ?baixar=<id> vem do link do e-mail. */
  readonly baixar = input<string>('');
  protected readonly busy = signal(false);
  protected readonly report = signal<RestoreReport | null>(null);
  protected readonly stored = rxResource({ stream: () => this.api.storedBackups() });
  protected readonly emailConfigured = computed(
    () => this.session.integrations().find((i) => i.key === 'EMAIL')?.configured ?? false,
  );
  protected readonly toRestore = computed(
    () => this.report()?.tables.reduce((n, t) => n + t.restored, 0) ?? 0,
  );
  protected to = 'voefamilhas@gmail.com';
  private file: File | null = null;

  constructor() {
    void this.session.ready();
  }

  protected dateTime(value: string): string {
    return value ? formatDateTime(value) : '—';
  }

  protected size(bytes: number): string {
    return fileSize(bytes);
  }

  protected exportNow(): Promise<void> {
    return this.run(async () =>
      saveDownload(await firstValueFrom(this.api.exportBackup()), 'backup-crm-voe-familhas.json'),
    );
  }

  protected downloadStored(id: string): Promise<void> {
    return this.run(async () =>
      saveDownload(
        await firstValueFrom(this.api.downloadStoredBackup(id)),
        `backup-crm-${id}.json`,
      ),
    );
  }

  protected email(): Promise<void> {
    return this.run(async () => {
      const stored = await firstValueFrom(this.api.emailBackup(this.to.trim() || null));
      this.notices.info(`Backup guardado e link enviado para ${stored.emailedTo}.`);
      this.stored.reload();
    });
  }

  protected async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
    this.report.set(null);
    if (!this.file) return;
    await this.run(async () =>
      this.report.set(await firstValueFrom(this.api.restoreBackup(this.file!, false))),
    );
  }

  protected async apply(): Promise<void> {
    if (
      !this.file ||
      !confirm(`Restaurar ${this.toRestore()} registros apagados? O que existe hoje não muda.`)
    )
      return;
    await this.run(async () => {
      this.report.set(await firstValueFrom(this.api.restoreBackup(this.file!, true)));
      this.notices.info('Restauração concluída.');
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
