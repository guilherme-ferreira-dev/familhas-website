import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import type { PostStatus } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { StatusBadge } from '../../shared/status-badge';
import type { Tone } from '../../shared/labels';

export const POST_STATUS: Record<PostStatus, { label: string; tone: Tone }> = {
  DRAFT: { label: 'Rascunho', tone: 'neutral' },
  READY: { label: 'Pronto', tone: 'success' },
  PUBLISHING: { label: 'Publicando', tone: 'warning' },
  PUBLISHED: { label: 'Publicado', tone: 'success' },
  FAILED: { label: 'Falhou', tone: 'danger' },
  UNCERTAIN: { label: 'Conferir no Instagram', tone: 'danger' },
};

export const POST_FORMAT = { STORY: 'Story', FEED: 'Feed', REEL: 'Reel' } as const;

const QUEUE: PostStatus[] = ['DRAFT', 'READY', 'PUBLISHING', 'FAILED', 'UNCERTAIN'];

/** Criar posts Insta: a fila (rascunhos, prontos, com problema) e os publicados. */
@Component({
  selector: 'app-instagram-posts-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Pager, LoadError, StatusBadge],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Criar posts Insta</h1>
          <p>Story, Feed (foto ou carrossel) e Reel. Publicar exige revisão.</p>
        </div>
        @if (session.can('crm:posts-insta:criar')) {
          <a class="btn btn--primary" routerLink="/posts-insta/novo">Novo post</a>
        }
      </div>
      <nav class="tabs" aria-label="Situação">
        <a
          [routerLink]="[]"
          [queryParams]="{ aba: null, pagina: null }"
          [attr.aria-current]="!published() ? 'page' : null"
          >Fila</a
        >
        <a
          [routerLink]="[]"
          [queryParams]="{ aba: 'publicados', pagina: null }"
          [attr.aria-current]="published() ? 'page' : null"
          >Publicados</a
        >
      </nav>
      @if (posts.error()) {
        <app-load-error [error]="posts.error()" (retry)="posts.reload()" />
      }
      <div class="table-wrap" [attr.aria-busy]="posts.isLoading()">
        <table>
          <caption class="visually-hidden">
            Posts do Instagram
          </caption>
          <thead>
            <tr>
              <th scope="col">Título</th>
              <th scope="col">Formato</th>
              <th scope="col">Lâminas</th>
              <th scope="col">Situação</th>
              <th scope="col">Criado</th>
            </tr>
          </thead>
          <tbody>
            @for (p of posts.value()?.content ?? []; track p.id) {
              <tr>
                <td>
                  <a [routerLink]="['/posts-insta', p.id]">{{ p.title }}</a>
                </td>
                <td>{{ format[p.format] }}</td>
                <td class="num">{{ p.hasVideo ? 'vídeo' : p.slideCount }}</td>
                <td>
                  <app-status-badge
                    [label]="status[p.status].label"
                    [tone]="status[p.status].tone"
                  />
                </td>
                <td>{{ dateTime(p.createdAt) }} · {{ p.createdBy ?? '—' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="empty">
                  {{ posts.isLoading() ? 'Carregando…' : 'Nenhum post.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="posts.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
})
export class InstagramPostsPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  protected readonly session = inject(CrmSession);
  protected readonly status = POST_STATUS;
  protected readonly format = POST_FORMAT;

  readonly aba = input<string>('');
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });
  protected readonly published = computed(() => this.aba() === 'publicados');
  protected readonly posts = rxResource({
    params: () => ({ published: this.published(), page: this.pagina() }),
    stream: ({ params }) =>
      this.api.instagramPosts(params.published ? ['PUBLISHED'] : QUEUE, params.page),
  });

  constructor() {
    void this.session.ready();
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
