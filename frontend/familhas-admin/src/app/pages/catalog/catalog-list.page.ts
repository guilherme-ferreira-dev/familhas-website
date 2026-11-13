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
import { CatalogApi } from '../../core/admin/catalog-api';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { DEFINITIONS } from './catalog-definitions';

interface Row {
  id: string;
  status: string;
}

/** Lista de um recurso do catálogo (definição em catalog-definitions.ts). */
@Component({
  selector: 'app-catalog-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Pager, LoadError],
  template: `
    @let d = def();
    <div class="page-header">
      <h2>{{ d.title }}</h2>
      <div class="actions">
        <label for="status" class="visually-hidden">Status</label>
        <select
          id="status"
          [value]="status() ?? ''"
          (change)="filterStatus($any($event.target).value)"
        >
          <option value="">Ativos e inativos</option>
          <option value="ACTIVE" [selected]="status() === 'ACTIVE'">Só ativos</option>
          <option value="INACTIVE" [selected]="status() === 'INACTIVE'">Só inativos</option>
        </select>
        <a class="btn btn--primary" routerLink="novo">Novo {{ d.singular }}</a>
      </div>
    </div>
    @if (items.error()) {
      <app-load-error [error]="items.error()" (retry)="items.reload()" />
    }
    <div class="table-wrap">
      <table>
        <caption class="visually-hidden">
          {{
            d.title
          }}
        </caption>
        <thead>
          <tr>
            @for (c of d.columns; track c.header) {
              <th scope="col" [class.num]="c.num">{{ c.header }}</th>
            }
            <th scope="col"><span class="visually-hidden">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          @for (item of rows(); track item.id) {
            <tr>
              @for (c of d.columns; track c.header) {
                <td [class.num]="c.num">{{ c.value(item) }}</td>
              }
              <td class="actions">
                <a class="btn btn--small" [routerLink]="item.id">Editar</a>
                <button type="button" class="btn btn--small btn--danger" (click)="remove(item)">
                  Excluir
                </button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="d.columns.length + 1" class="empty">Nada cadastrado.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    <app-pager [meta]="items.value() ?? null" (pageChange)="goToPage($event)" />
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--space-3);
    }
  `,
})
export class CatalogListPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly tipo = input.required<string>();
  readonly status = input<string>();
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly def = computed(() => DEFINITIONS[this.tipo()] ?? DEFINITIONS['voos']);

  protected readonly items = rxResource({
    params: () => ({ def: this.def(), status: this.status() || null, page: this.pagina() }),
    stream: ({ params }) =>
      this.api.list(params.def.resource, {
        ...params.def.extraParams,
        status: params.status,
        page: params.page,
        size: 20,
        sort: params.def.sort,
      }),
  });
  protected readonly rows = computed(() => (this.items.value()?.content ?? []) as Row[]);

  protected filterStatus(status: string): void {
    void this.router.navigate([], { queryParams: { status: status || null } });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected remove(item: Row): void {
    const d = this.def();
    if (!confirm(`Excluir este ${d.singular}? Se ele estiver em uso, inative em vez de excluir.`))
      return;
    this.api.delete(d.resource, item.id).subscribe({
      next: () => {
        this.notifications.info(`${capitalize(d.singular)} excluído.`);
        this.items.reload();
      },
      error: (error: unknown) => this.notifications.error(errorMessage(error)),
    });
  }
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
