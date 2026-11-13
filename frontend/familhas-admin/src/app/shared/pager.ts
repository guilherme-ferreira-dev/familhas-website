import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { PageMeta } from '../core/api/api-types';

/** Paginação das listas (page é 0-based, como na API). */
@Component({
  selector: 'app-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (meta(); as m) {
      <nav class="pager" aria-label="Paginação">
        <span class="muted">{{ summary() }}</span>
        <div class="actions">
          <button
            type="button"
            class="btn btn--small"
            [disabled]="m.page === 0"
            (click)="pageChange.emit(m.page - 1)"
          >
            Anterior
          </button>
          <span aria-current="page">Página {{ m.page + 1 }} de {{ m.totalPages || 1 }}</span>
          <button
            type="button"
            class="btn btn--small"
            [disabled]="m.page + 1 >= m.totalPages"
            (click)="pageChange.emit(m.page + 1)"
          >
            Próxima
          </button>
        </div>
      </nav>
    }
  `,
  styles: `
    .pager {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: center;
      justify-content: space-between;
    }
  `,
})
export class Pager {
  readonly meta = input<PageMeta | null>(null);
  readonly pageChange = output<number>();

  protected readonly summary = computed(() => {
    const m = this.meta();
    if (!m) return '';
    return `${m.totalElements} registro${m.totalElements === 1 ? '' : 's'}`;
  });
}
