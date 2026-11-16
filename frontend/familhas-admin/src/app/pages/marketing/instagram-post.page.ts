import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { CrmFile, InstagramPost, PostFormat } from '../../core/api/crm-types';
import { BrandStore } from '../../core/crm/brand-store';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { download, fileToImage, loadImage, slug, toBlob } from '../../shared/marketing/canvas';
import {
  drawBrandedSlide,
  drawCleanSlide,
  footerText,
  slideSize,
} from '../../shared/marketing/insta-slide';
import { zip, ZipEntry } from '../../shared/marketing/zip';
import { StatusBadge } from '../../shared/status-badge';
import { captionsFile, fullCaption, postProblems, SlideDraft, toRequest } from './instagram-editor';
import { POST_FORMAT, POST_STATUS } from './instagram-posts.page';

/**
 * Post do Instagram: formato, ideia, lâminas (cada uma com imagem final com marca e cópia "-limpa"), Reel com o MP4
 * enviado (usado como veio, sem cortes), legenda e hashtags, ZIP, revisão ("pronto") e publicação pela Meta.
 */
@Component({
  selector: 'app-instagram-post-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, StatusBadge],
  template: `
    <section class="page" aria-labelledby="titulo">
      <p><a routerLink="/posts-insta">← Criar posts Insta</a></p>
      <div class="page-header">
        <div>
          <h1 id="titulo">{{ id() ? title || 'Post' : 'Novo post' }}</h1>
          @if (post(); as p) {
            <p>
              <app-status-badge
                [label]="statusInfo[p.status].label"
                [tone]="statusInfo[p.status].tone"
              />
              @if (p.permalink) {
                ·
                <a [href]="p.permalink" target="_blank" rel="noopener noreferrer"
                  >Ver no Instagram</a
                >
              }
            </p>
          }
        </div>
      </div>

      @if (post()?.lastError; as message) {
        <p class="alert" role="alert">{{ message }}</p>
      }
      @if (post()?.status === 'UNCERTAIN' && session.can('crm:posts-insta:publicar')) {
        <div class="alert" role="alert">
          <p>
            A Meta pode ter publicado este post. Confira no perfil do Instagram antes de qualquer
            coisa: o CRM não reenvia sozinho.
          </p>
          <div class="actions">
            <input
              aria-label="Link do post publicado (opcional)"
              [(ngModel)]="permalink"
              placeholder="https://www.instagram.com/p/..."
            />
            <button type="button" class="btn" (click)="resolve(true)">Foi publicado</button>
            <button type="button" class="btn" (click)="resolve(false)">Não foi publicado</button>
          </div>
        </div>
      }
      @if (problems().length) {
        <ul class="alert" role="alert">
          @for (p of problems(); track p) {
            <li>{{ p }}</li>
          }
        </ul>
      }

      <fieldset class="card" [disabled]="locked()">
        <legend>Post</legend>
        <div class="form-grid">
          <div class="field">
            <label for="titulo-post">Título interno *</label>
            <input id="titulo-post" [(ngModel)]="title" maxlength="120" />
          </div>
          <div class="field">
            <label for="formato">Formato</label>
            <select id="formato" [(ngModel)]="format">
              <option value="FEED">Feed 1:1 (foto ou carrossel)</option>
              <option value="STORY">Story 9:16</option>
              <option value="REEL">Reel 9:16 (vídeo)</option>
            </select>
          </div>
          <div class="field field--wide">
            <label for="ideia">Ideia / foco (até 2.000 caracteres)</label>
            <textarea id="ideia" rows="2" maxlength="2000" [(ngModel)]="idea"></textarea>
          </div>
        </div>
        @if (!aiConfigured()) {
          <p class="muted">
            Geração por IA (textos, imagens por lâmina, modo cidade e Reel de 6 s) não configurada:
            monte as lâminas com as suas fotos.
          </p>
        }
      </fieldset>

      @if (format === 'REEL') {
        <fieldset class="card" [disabled]="locked()">
          <legend>Vídeo do Reel</legend>
          @if (video(); as v) {
            <p>
              {{ v.name }}
              <button type="button" class="btn btn--small" (click)="video.set(null)">Trocar</button>
            </p>
          } @else {
            <div class="field">
              <label for="video"
                >MP4 vertical 9:16 (até 100 MB, usado exatamente como enviado)</label
              >
              <input id="video" type="file" accept="video/mp4" (change)="uploadVideo($event)" />
            </div>
          }
        </fieldset>
      }

      <fieldset class="card" [disabled]="locked()">
        <legend>
          {{ format === 'REEL' ? 'Capa (opcional)' : 'Lâminas (' + slides().length + ')' }}
        </legend>
        <ol class="slides">
          @for (s of slides(); track s.imageFileId; let i = $index) {
            <li class="slide">
              @if (s.previewUrl) {
                <img [src]="s.previewUrl" [alt]="'Lâmina ' + (i + 1)" />
              }
              <div class="form-grid">
                <div class="field">
                  <label [for]="'gancho-' + i">Gancho (destaque)</label>
                  <input [id]="'gancho-' + i" [(ngModel)]="s.hook" maxlength="120" />
                </div>
                <div class="field">
                  <label [for]="'tit-' + i">Título</label>
                  <input [id]="'tit-' + i" [(ngModel)]="s.title" maxlength="120" />
                </div>
                <div class="field field--wide">
                  <label [for]="'top-' + i">Tópicos (3 a 5, um por linha)</label>
                  <textarea [id]="'top-' + i" rows="3" [(ngModel)]="s.bullets"></textarea>
                </div>
                <div class="field field--wide">
                  <label [for]="'leg-' + i">Legenda da lâmina (2 a 4 frases)</label>
                  <textarea
                    [id]="'leg-' + i"
                    rows="2"
                    maxlength="1000"
                    [(ngModel)]="s.caption"
                  ></textarea>
                </div>
              </div>
              <div class="actions">
                <button
                  type="button"
                  class="btn btn--small"
                  [disabled]="busy()"
                  (click)="recompose(i)"
                >
                  Atualizar texto na imagem
                </button>
                <button
                  type="button"
                  class="btn btn--small"
                  [disabled]="i === 0"
                  (click)="move(i, -1)"
                >
                  Subir
                </button>
                <button
                  type="button"
                  class="btn btn--small"
                  [disabled]="i === slides().length - 1"
                  (click)="move(i, 1)"
                >
                  Descer
                </button>
                <button type="button" class="btn btn--small" (click)="copy(s.caption)">
                  Copiar legenda
                </button>
                <button type="button" class="btn btn--small" (click)="remove(i)">Remover</button>
              </div>
            </li>
          }
        </ol>
        @if (canAddSlide()) {
          <div class="field">
            <label for="nova"
              >Adicionar {{ format === 'REEL' ? 'capa' : 'lâmina' }} (JPG, PNG ou WEBP)</label
            >
            <input
              id="nova"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              [disabled]="busy()"
              (change)="addSlide($event)"
            />
            <small class="muted"
              >A imagem recebe a logo no topo, o gancho, o título, os tópicos e o rodapé com o
              WhatsApp.</small
            >
          </div>
        }
      </fieldset>

      <fieldset class="card" [disabled]="locked()">
        <legend>Legenda</legend>
        <div class="field">
          <label for="legenda">Legenda (até 2.200 caracteres com as hashtags)</label>
          <textarea id="legenda" rows="5" maxlength="2200" [(ngModel)]="caption"></textarea>
        </div>
        <div class="field">
          <label for="tags">Hashtags (até 30)</label>
          <input id="tags" [(ngModel)]="hashtags" maxlength="500" />
        </div>
      </fieldset>

      <div class="actions">
        @if (!locked()) {
          <button type="button" class="btn btn--primary" [disabled]="busy()" (click)="save()">
            Salvar
          </button>
        }
        @if (post(); as p) {
          @if (
            (p.status === 'DRAFT' || p.status === 'FAILED') && session.can('crm:posts-insta:editar')
          ) {
            <button type="button" class="btn" [disabled]="busy()" (click)="act('ready')">
              Revisado: marcar como pronto
            </button>
          }
          @if (
            (p.status === 'READY' || p.status === 'FAILED') &&
            session.can('crm:posts-insta:publicar')
          ) {
            <button
              type="button"
              class="btn btn--primary"
              [disabled]="busy() || !metaConfigured()"
              (click)="act('publish')"
            >
              Publicar no Instagram
            </button>
            @if (!metaConfigured()) {
              <span class="muted">Publicação pela Meta não configurada no servidor.</span>
            }
          }
          @if (p.status === 'PUBLISHING') {
            <button type="button" class="btn" (click)="reload()">Atualizar situação</button>
          }
        }
        <button type="button" class="btn" (click)="copy(fullCaptionText())">
          Copiar legenda com hashtags
        </button>
        <button
          type="button"
          class="btn"
          [disabled]="!slides().length || busy()"
          (click)="downloadZip()"
        >
          Baixar ZIP
        </button>
        @if (post() && !locked() && session.can('crm:posts-insta:excluir')) {
          <button type="button" class="btn btn--danger" [disabled]="busy()" (click)="deletePost()">
            Excluir
          </button>
        }
      </div>
    </section>
  `,
  styles: `
    fieldset.card {
      margin: 0 0 var(--space-3);
    }
    legend {
      font-weight: 700;
    }
    .slides {
      margin: 0 0 var(--space-3);
      padding-inline-start: var(--space-4);
      display: grid;
      gap: var(--space-4);
    }
    .slide {
      display: grid;
      gap: var(--space-2);
    }
    .slide img {
      max-width: 16rem;
      border-radius: var(--radius-sm);
      box-shadow: var(--shadow);
    }
  `,
})
export class InstagramPostPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  private readonly notices = inject(NotificationService);
  private readonly brand = inject(BrandStore);
  protected readonly session = inject(CrmSession);
  protected readonly statusInfo = POST_STATUS;
  protected readonly formats = POST_FORMAT;

  readonly id = input<string>('');
  protected readonly post = signal<InstagramPost | null>(null);
  protected readonly slides = signal<SlideDraft[]>([]);
  protected readonly video = signal<CrmFile | null>(null);
  protected readonly busy = signal(false);
  protected readonly problems = signal<string[]>([]);
  protected readonly locked = computed(() => {
    const status = this.post()?.status;
    return status === 'PUBLISHING' || status === 'PUBLISHED' || status === 'UNCERTAIN';
  });
  protected readonly aiConfigured = computed(
    () => this.configured('AI_TEXT') && this.configured('AI_IMAGE'),
  );
  protected readonly metaConfigured = computed(() => this.configured('INSTAGRAM'));

  protected title = '';
  protected format: PostFormat = 'FEED';
  protected idea = '';
  protected caption = '';
  protected hashtags = '#voefamilhas #viagem';
  protected permalink = '';

  constructor() {
    void this.session.ready();
    effect(() => {
      const id = this.id();
      if (id) void this.load(id);
    });
  }

  protected canAddSlide(): boolean {
    const n = this.slides().length;
    return this.format === 'FEED' ? n < 20 : n < 1;
  }

  protected fullCaptionText(): string {
    return fullCaption(this.caption, this.hashtags);
  }

  protected async addSlide(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    await this.run(async () => {
      const image = await fileToImage(file);
      const draft: SlideDraft = {
        imageFileId: '',
        cleanFileId: null,
        previewUrl: null,
        hook: '',
        title: '',
        bullets: '',
        caption: '',
      };
      const { clean, branded, preview } = await this.compose(image, draft);
      this.slides.update((list) => [
        ...list,
        { ...draft, imageFileId: branded, cleanFileId: clean, previewUrl: preview },
      ]);
    });
    input.value = '';
  }

  /** Reescreve o texto da lâmina a partir da cópia "-limpa", sem precisar da foto original. */
  protected async recompose(index: number): Promise<void> {
    const slide = this.slides()[index];
    if (!slide.cleanFileId) {
      this.notices.error('Esta lâmina não tem a cópia limpa para reescrever o texto.');
      return;
    }
    await this.run(async () => {
      const link = await firstValueFrom(this.api.fileLink(slide.cleanFileId!));
      const image = await loadImage(this.api.absolute(link.path));
      const { branded, preview } = await this.compose(image, slide, false);
      this.slides.update((list) =>
        list.map((s, i) => (i === index ? { ...s, imageFileId: branded, previewUrl: preview } : s)),
      );
    });
  }

  protected move(index: number, delta: number): void {
    this.slides.update((list) => {
      const copy = [...list];
      [copy[index], copy[index + delta]] = [copy[index + delta], copy[index]];
      return copy;
    });
  }

  protected remove(index: number): void {
    this.slides.update((list) => list.filter((_, i) => i !== index));
  }

  protected async uploadVideo(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    await this.run(async () =>
      this.video.set(await firstValueFrom(this.api.uploadFile('INSTAGRAM_MEDIA', file))),
    );
    input.value = '';
  }

  protected async save(): Promise<void> {
    const problems = postProblems(this.format, this.slides(), !!this.video(), this.hashtags);
    if (!this.title.trim()) problems.unshift('Dê um título interno ao post.');
    this.problems.set(problems);
    if (problems.length) return;
    await this.run(async () => {
      const body = toRequest({
        format: this.format,
        title: this.title,
        idea: this.idea,
        caption: this.caption,
        hashtags: this.hashtags,
        slides: this.slides(),
        videoFileId: this.video()?.id ?? null,
        version: this.post()?.version ?? null,
        origin: this.post()?.origin ?? 'MANUAL',
      });
      const saved = this.id()
        ? await firstValueFrom(this.api.updateInstagramPost(this.id(), body))
        : await firstValueFrom(this.api.createInstagramPost(body));
      this.notices.info('Post salvo.');
      if (!this.id()) await this.router.navigate(['/posts-insta', saved.id]);
      else this.apply(saved);
    });
  }

  protected async act(action: 'ready' | 'publish'): Promise<void> {
    if (action === 'publish' && !confirm('Publicar agora no Instagram da agência?')) return;
    await this.run(async () =>
      this.apply(await firstValueFrom(this.api.instagramPostAction(this.id(), action))),
    );
  }

  protected async resolve(published: boolean): Promise<void> {
    await this.run(async () =>
      this.apply(
        await firstValueFrom(
          this.api.resolveInstagramPost(this.id(), published, this.permalink.trim() || null),
        ),
      ),
    );
  }

  protected reload(): Promise<void> {
    return this.load(this.id());
  }

  protected async deletePost(): Promise<void> {
    if (!confirm('Excluir o post e as imagens?')) return;
    await this.run(async () => {
      await firstValueFrom(this.api.deleteInstagramPost(this.id()));
      await this.router.navigateByUrl('/posts-insta');
    });
  }

  protected async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.notices.info('Copiado.');
    } catch {
      this.notices.error('Não foi possível copiar. Selecione o texto e copie manualmente.');
    }
  }

  protected async downloadZip(): Promise<void> {
    await this.run(async () => {
      const entries: ZipEntry[] = [];
      for (const [i, s] of this.slides().entries()) {
        const link = await firstValueFrom(this.api.fileLink(s.imageFileId));
        const blob = await (await fetch(this.api.absolute(link.path))).blob();
        entries.push({
          name: `lamina-${String(i + 1).padStart(2, '0')}.jpg`,
          data: new Uint8Array(await blob.arrayBuffer()),
        });
      }
      entries.push({
        name: 'legendas.txt',
        data: new TextEncoder().encode(
          captionsFile(this.title, this.caption, this.hashtags, this.slides()),
        ),
      });
      download(zip(entries), `${slug(this.title || 'post')}.zip`);
    });
  }

  /** Compõe e envia a cópia limpa (opcional) e a imagem com marca, ambas em JPEG para a Meta. */
  private async compose(
    image: HTMLImageElement,
    s: SlideDraft,
    withClean = true,
  ): Promise<{ clean: string | null; branded: string; preview: string }> {
    const fmt = this.format;
    const { width, height } = slideSize(fmt);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d')!;
    const name = slug(this.title || 'lamina');
    let clean: string | null = s.cleanFileId;
    if (withClean) {
      drawCleanSlide(ctx, fmt, image);
      const cleanBlob = await toBlob(canvas, 'image/jpeg');
      clean = (
        await firstValueFrom(
          this.api.uploadFile(
            'INSTAGRAM_MEDIA',
            new File([cleanBlob], `${name}-limpa.jpg`, { type: 'image/jpeg' }),
          ),
        )
      ).id;
    }
    drawBrandedSlide(ctx, fmt, {
      image,
      hook: s.hook,
      title: s.title,
      bullets: s.bullets.split('\n'),
      logo: await this.brand.logoImage(),
      footer: footerText(this.session.company()?.whatsapp ?? ''),
    });
    const brandedBlob = await toBlob(canvas, 'image/jpeg');
    const branded = (
      await firstValueFrom(
        this.api.uploadFile(
          'INSTAGRAM_MEDIA',
          new File([brandedBlob], `${name}.jpg`, { type: 'image/jpeg' }),
        ),
      )
    ).id;
    return { clean, branded, preview: URL.createObjectURL(brandedBlob) };
  }

  private async load(id: string): Promise<void> {
    try {
      this.apply(await firstValueFrom(this.api.instagramPost(id)));
    } catch (error) {
      this.notices.error(errorMessage(error));
    }
  }

  private apply(p: InstagramPost): void {
    this.post.set(p);
    this.title = p.title;
    this.format = p.format;
    this.idea = p.idea ?? '';
    this.caption = p.caption ?? '';
    this.hashtags = p.hashtags ?? '';
    this.video.set(p.video ?? null);
    this.slides.set(
      p.slides.map((s) => ({
        imageFileId: s.image.id,
        cleanFileId: s.clean?.id ?? null,
        previewUrl: null,
        hook: s.hook ?? '',
        title: s.title ?? '',
        bullets: s.bullets.join('\n'),
        caption: s.caption ?? '',
      })),
    );
    void this.loadPreviews();
  }

  private async loadPreviews(): Promise<void> {
    for (const s of this.slides()) {
      try {
        const link = await firstValueFrom(this.api.fileLink(s.imageFileId));
        const url = this.api.absolute(link.path);
        this.slides.update((list) =>
          list.map((x) => (x.imageFileId === s.imageFileId ? { ...x, previewUrl: url } : x)),
        );
      } catch {
        // sem prévia: a lâmina continua editável
      }
    }
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

  private configured(key: string): boolean {
    return this.session.integrations().find((i) => i.key === key)?.configured ?? false;
  }
}
