import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { CrmFile } from '../core/api/crm-types';
import { CrmApi } from '../core/crm/crm-api';
import { errorMessage } from '../core/errors/api-error';
import { fileSize } from './crm-format';

/** Páginas renderizadas no máximo: documento de viagem raramente passa disso. */
const MAX_PAGES = 20;

/**
 * Visualiza um arquivo privado: pede um link assinado (5 min, auditado) e mostra o PDF pelo PDF.js (worker servido
 * pela própria origem; a versão 6 não usa eval nem roda scripts do PDF) ou a imagem. Também oferece abrir/baixar pelo mesmo link.
 */
@Component({
  selector: 'app-file-viewer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="viewer card" [attr.aria-labelledby]="'arquivo-' + file().id">
      <header>
        <h2 [id]="'arquivo-' + file().id">{{ file().name }}</h2>
        <span class="muted">{{ size() }}</span>
        <div class="actions">
          @if (url(); as link) {
            <a class="btn btn--small" [href]="link" target="_blank" rel="noopener noreferrer"
              >Abrir / baixar</a
            >
          }
          <button type="button" class="btn btn--small" (click)="closed.emit()">Fechar</button>
        </div>
      </header>
      @if (error(); as message) {
        <p class="alert" role="alert">{{ message }}</p>
      } @else if (!url()) {
        <p class="muted">Carregando…</p>
      }
      @if (url() && isImage()) {
        <img [src]="url()" [alt]="file().name" />
      }
      <div #pages class="pages" [attr.aria-busy]="rendering()"></div>
      @if (truncated()) {
        <p class="muted">
          Mostrando as primeiras {{ maxPages }} páginas. Abra o arquivo para ver tudo.
        </p>
      }
    </section>
  `,
  styles: `
    header {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: baseline;
      margin-bottom: var(--space-3);
    }
    h2 {
      margin: 0;
      font-size: 1rem;
      overflow-wrap: anywhere;
    }
    .actions {
      margin-inline-start: auto;
    }
    img {
      display: block;
      max-width: 100%;
      height: auto;
    }
    .pages {
      display: grid;
      gap: var(--space-3);
    }
    .pages :is(canvas) {
      max-width: 100%;
      height: auto;
      box-shadow: var(--shadow);
    }
  `,
})
export class FileViewer {
  private readonly api = inject(CrmApi);
  readonly file = input.required<CrmFile>();
  readonly closed = output<void>();

  protected readonly url = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly rendering = signal(false);
  protected readonly truncated = signal(false);
  protected readonly maxPages = MAX_PAGES;
  private readonly pages = viewChild.required<ElementRef<HTMLElement>>('pages');

  constructor() {
    effect(() => {
      const file = this.file();
      void this.load(file);
    });
  }

  protected isImage(): boolean {
    return this.file().contentType.startsWith('image/');
  }

  protected size(): string {
    return fileSize(this.file().sizeBytes);
  }

  private async load(file: CrmFile): Promise<void> {
    this.url.set(null);
    this.error.set(null);
    this.truncated.set(false);
    this.pages().nativeElement.replaceChildren();
    try {
      const link = await firstValueFrom(this.api.fileLink(file.id));
      this.url.set(this.api.absolute(link.path));
      if (file.contentType === 'application/pdf') {
        await this.renderPdf(this.url()!);
      }
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  private async renderPdf(url: string): Promise<void> {
    this.rendering.set(true);
    try {
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = '/pdfjs/pdf.worker.min.mjs';
      const pdf = await pdfjs.getDocument({ url, withCredentials: false }).promise;
      const total = Math.min(pdf.numPages, MAX_PAGES);
      this.truncated.set(pdf.numPages > MAX_PAGES);
      const container = this.pages().nativeElement;
      for (let number = 1; number <= total; number++) {
        const page = await pdf.getPage(number);
        const viewport = page.getViewport({ scale: 1.5 });
        const canvas = document.createElement('canvas');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.setAttribute('role', 'img');
        canvas.setAttribute('aria-label', `Página ${number} de ${pdf.numPages}`);
        container.appendChild(canvas);
        await page.render({ canvas, viewport }).promise;
      }
    } finally {
      this.rendering.set(false);
    }
  }
}
