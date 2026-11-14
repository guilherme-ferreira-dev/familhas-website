import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import type { Client, ClientSegment } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { ENTITY_STATUS } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';
import { StatusBadge } from '../../shared/status-badge';

type Tab = ClientSegment | 'ALL';

const TABS: { value: Tab; label: string; hint: string }[] = [
  {
    value: 'NEW',
    label: 'Novos',
    hint: 'Cadastrados há 30 dias ou menos, sem reserva em andamento ou concluída',
  },
  { value: 'ACTIVE', label: 'Ativos', hint: 'Com reserva aguardando pagamento ou confirmada' },
  {
    value: 'PAST',
    label: 'Passados',
    hint: 'Só reservas concluídas ou canceladas, ou cliente inativo',
  },
  { value: 'ALL', label: 'Todos', hint: 'Todos os clientes' },
];

/** Clientes com abas novos, ativos e passados, e busca por nome ou e-mail. Estado na URL. */
@Component({
  selector: 'app-clients-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Pager, StatusBadge, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Clientes</h1>
          <p>{{ currentTab().hint }}.</p>
        </div>
      </div>

      <nav class="tabs" aria-label="Abas de clientes">
        @for (t of tabs; track t.value) {
          <a
            [routerLink]="[]"
            [queryParams]="{ aba: t.value, q: q() || null, pagina: null }"
            [attr.aria-current]="tab() === t.value ? 'page' : null"
          >
            {{ t.label }}
            @if (counts.value(); as c) {
              <span class="count">{{ count(t.value, c) }}</span>
            }
          </a>
        }
      </nav>

      <form class="filters" role="search" (submit)="search($event, busca.value)">
        <div class="field field--wide">
          <label for="busca">Buscar por nome ou e-mail</label>
          <input id="busca" #busca type="search" [value]="q()" maxlength="100" />
        </div>
        <button type="submit" class="btn btn--primary">Buscar</button>
      </form>

      @if (clients.error()) {
        <app-load-error [error]="clients.error()" (retry)="clients.reload()" />
      }
      <div class="table-wrap" [attr.aria-busy]="clients.isLoading()">
        <table>
          <caption class="visually-hidden">
            Clientes:
            {{
              currentTab().label
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">E-mail</th>
              <th scope="col">Telefone</th>
              <th scope="col">Login</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            @for (c of clients.value()?.content ?? []; track c.id) {
              <tr>
                <td>
                  <a [routerLink]="['/clientes', c.id]">{{ c.name }}</a>
                </td>
                <td>{{ c.email }}</td>
                <td>{{ c.phoneNumber ?? '—' }}</td>
                <td>{{ c.hasLogin ? 'Sim' : 'Não' }}</td>
                <td><app-status-badge [label]="status(c).label" [tone]="status(c).tone" /></td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="empty">
                  {{ clients.isLoading() ? 'Carregando…' : 'Nenhum cliente.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="clients.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
})
export class ClientsPage {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);

  /** Query params (?aba=&q=&pagina=). */
  readonly aba = input<string>();
  readonly q = input<string>('');
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly tabs = TABS;
  protected readonly tab = computed<Tab>(() => {
    const value = this.aba() as Tab;
    return TABS.some((t) => t.value === value) ? value : 'ACTIVE';
  });
  protected readonly currentTab = computed(() => TABS.find((t) => t.value === this.tab())!);

  protected readonly counts = rxResource({ stream: () => this.api.clientSegments() });
  protected readonly clients = rxResource({
    params: () => ({ tab: this.tab(), q: this.q(), page: this.pagina() }),
    stream: ({ params }) =>
      this.api.clients({
        segment: params.tab === 'ALL' ? null : params.tab,
        q: params.q,
        page: params.page,
        size: 20,
        sort: 'name,asc',
      }),
  });

  protected status(c: Client) {
    return ENTITY_STATUS[c.status];
  }

  protected count(tab: Tab, c: { all: number; new: number; active: number; past: number }): number {
    return { ALL: c.all, NEW: c.new, ACTIVE: c.active, PAST: c.past }[tab];
  }

  protected search(event: Event, q: string): void {
    event.preventDefault();
    void this.router.navigate([], { queryParams: { aba: this.tab(), q: q.trim() || null } });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
