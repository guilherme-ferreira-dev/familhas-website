import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { CrmFile } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { fileSize } from '../../shared/crm-format';
import { FileViewer } from '../../shared/file-viewer';
import { formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';

/**
 * Documentos: repositório privado de PDFs e imagens da agência, com busca por nome e visualização no navegador
 * (PDF.js). Download por link assinado de 5 minutos. Fica fora do backup central (JSON).
 */
@Component({
  selector: 'app-documents-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Pager, FileViewer],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Documentos</h1>
          <p>PDFs e imagens privados da agência. Não entram no backup central.</p>
        </div>
      </div>

      <form class="filters" role="search" (submit)="search($event, busca.value)">
        <div class="field field--wide">
          <label for="busca">Buscar por nome</label>
          <input id="busca" #busca type="search" [value]="q()" maxlength="200" />
        </div>
        <button type="submit" class="btn btn--primary">Buscar</button>
        @if (session.can('crm:documentos:criar')) {
          <div class="field">
            <label for="envio">Enviar (PDF até 15 MB, imagem até 10 MB)</label>
            <input
              id="envio"
              type="file"
              accept="application/pdf,image/jpeg,image/png,image/webp"
              [disabled]="busy()"
              (change)="upload($event)"
            />
          </div>
        }
      </form>

      @if (files.error()) {
        <app-load-error [error]="files.error()" (retry)="files.reload()" />
      }
      <div class="table-wrap" [attr.aria-busy]="files.isLoading()">
        <table>
          <caption class="visually-hidden">
            Documentos
          </caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">Tipo</th>
              <th scope="col" class="num">Tamanho</th>
              <th scope="col">Enviado</th>
              <th scope="col"><span class="visually-hidden">Ações</span></th>
            </tr>
          </thead>
          <tbody>
            @for (f of files.value()?.content ?? []; track f.id) {
              <tr>
                <td>
                  <button type="button" class="link" (click)="viewing.set(f)">{{ f.name }}</button>
                </td>
                <td>{{ f.contentType === 'application/pdf' ? 'PDF' : 'Imagem' }}</td>
                <td class="num">{{ size(f) }}</td>
                <td>{{ dateTime(f.createdAt) }} · {{ f.uploadedBy }}</td>
                <td>
                  @if (session.can('crm:documentos:excluir')) {
                    <button
                      type="button"
                      class="btn btn--small"
                      [disabled]="busy()"
                      (click)="remove(f)"
                    >
                      Excluir
                    </button>
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="empty">
                  {{ files.isLoading() ? 'Carregando…' : 'Nenhum documento.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="files.value() ?? null" (pageChange)="goToPage($event)" />

      @if (viewing(); as file) {
        <app-file-viewer [file]="file" (closed)="viewing.set(null)" />
      }
    </section>
  `,
  styles: `
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
  `,
})
export class DocumentsPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  readonly q = input<string>('');
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly busy = signal(false);
  protected readonly viewing = signal<CrmFile | null>(null);
  protected readonly files = rxResource({
    params: () => ({ q: this.q(), page: this.pagina() }),
    stream: ({ params }) => this.api.files('DOCUMENT', params.q || null, params.page),
  });

  constructor() {
    void this.session.ready();
  }

  protected size(file: CrmFile): string {
    return fileSize(file.sizeBytes);
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected search(event: Event, value: string): void {
    event.preventDefault();
    void this.router.navigate([], { queryParams: { q: value.trim() || null } });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.busy.set(true);
    try {
      const stored = await firstValueFrom(this.api.uploadFile('DOCUMENT', file));
      this.notices.info(`${stored.name} enviado.`);
      this.files.reload();
    } catch (error) {
      this.notices.error(errorMessage(error));
    } finally {
      this.busy.set(false);
      input.value = '';
    }
  }

  protected async remove(file: CrmFile): Promise<void> {
    if (!confirm(`Excluir ${file.name}? Não dá para desfazer.`)) return;
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.deleteFile(file.id));
      if (this.viewing()?.id === file.id) this.viewing.set(null);
      this.files.reload();
    } catch (error) {
      this.notices.error(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
