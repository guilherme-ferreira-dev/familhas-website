import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { formatDateTime } from '../../shared/format';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';

/** Gerar Roteiro: propostas salvas, por destino ou cliente. */
@Component({
  selector: 'app-itineraries-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Pager, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Gerar Roteiro</h1>
          <p>Propostas comerciais em uma página A4, com a identidade da agência.</p>
        </div>
        @if (session.can('crm:roteiros:criar')) {
          <a class="btn btn--primary" routerLink="/roteiros/novo">Novo roteiro</a>
        }
      </div>
      <form class="filters" role="search" (submit)="search($event, busca.value)">
        <div class="field field--wide">
          <label for="busca">Destino ou cliente</label>
          <input id="busca" #busca type="search" [value]="q()" maxlength="100" />
        </div>
        <button type="submit" class="btn btn--primary">Buscar</button>
      </form>
      @if (list.error()) {
        <app-load-error [error]="list.error()" (retry)="list.reload()" />
      }
      <div class="table-wrap" [attr.aria-busy]="list.isLoading()">
        <table>
          <caption class="visually-hidden">
            Roteiros
          </caption>
          <thead>
            <tr>
              <th scope="col">Destino</th>
              <th scope="col">Dias</th>
              <th scope="col">Cliente</th>
              <th scope="col">Período</th>
              <th scope="col">Atualizado</th>
            </tr>
          </thead>
          <tbody>
            @for (i of list.value()?.content ?? []; track i.id) {
              <tr>
                <td>
                  <a [routerLink]="['/roteiros', i.id]">{{ i.destination }}</a>
                </td>
                <td class="num">{{ i.days }}</td>
                <td>{{ i.clientName ?? '—' }}</td>
                <td>{{ i.travelPeriod ?? '—' }}</td>
                <td>{{ dateTime(i.updatedAt) }} · {{ i.createdBy ?? '—' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="empty">
                  {{ list.isLoading() ? 'Carregando…' : 'Nenhum roteiro.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="list.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
})
export class ItinerariesPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  protected readonly session = inject(CrmSession);
  readonly q = input<string>('');
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });
  protected readonly list = rxResource({
    params: () => ({ q: this.q(), page: this.pagina() }),
    stream: ({ params }) => this.api.itineraries(params.q || null, params.page),
  });

  constructor() {
    void this.session.ready();
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
}
