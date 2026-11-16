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
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { formatLocalDateTime } from '../../shared/crm-format';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';

/**
 * Voos Clientes: pela data da ida, com busca rápida por passageiro na própria tela (sem acento, palavras em qualquer
 * ordem), histórico de arquivados e filtros. Estado na URL (?q=&aba=&de=&ate=&cia=&pagina=).
 */
@Component({
  selector: 'app-client-flights-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Pager, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Voos Clientes</h1>
          <p>Voos dos clientes pela data da ida. Horários locais do bilhete.</p>
        </div>
        @if (session.can('crm:voos-clientes:criar')) {
          <a class="btn btn--primary" routerLink="/voos-clientes/novo">Novo voo</a>
        }
      </div>

      <form class="filters" role="search" (submit)="search($event, busca.value)">
        <div class="field field--wide">
          <label for="busca">Buscar passageiro ou localizador</label>
          <input
            id="busca"
            #busca
            type="search"
            [value]="q()"
            maxlength="100"
            placeholder="Ex.: silva joao"
            autocomplete="off"
          />
        </div>
        <button type="submit" class="btn btn--primary">Buscar</button>
      </form>

      <nav class="tabs" aria-label="Situação dos voos">
        <a
          [routerLink]="[]"
          [queryParams]="{ aba: null, pagina: null }"
          queryParamsHandling="merge"
          [attr.aria-current]="!archived() ? 'page' : null"
          >Ativos</a
        >
        <a
          [routerLink]="[]"
          [queryParams]="{ aba: 'arquivados', pagina: null }"
          queryParamsHandling="merge"
          [attr.aria-current]="archived() ? 'page' : null"
          >Histórico (arquivados)</a
        >
      </nav>

      <details class="more-filters" [open]="!!(de() || ate() || cia())">
        <summary>Filtros</summary>
        <form class="filters" (submit)="filter($event, inicio.value, fim.value, companhia.value)">
          <div class="field">
            <label for="de">Ida a partir de</label>
            <input id="de" #inicio type="date" [value]="de()" />
          </div>
          <div class="field">
            <label for="ate">Ida até</label>
            <input id="ate" #fim type="date" [value]="ate()" />
          </div>
          <div class="field">
            <label for="cia">Companhia</label>
            <input id="cia" #companhia type="text" [value]="cia()" maxlength="60" />
          </div>
          <div class="actions">
            <button type="submit" class="btn">Aplicar</button>
            <a class="btn" [routerLink]="[]" [queryParams]="{ q: q() || null, aba: aba() || null }"
              >Limpar</a
            >
          </div>
        </form>
      </details>

      @if (flights.error()) {
        <app-load-error [error]="flights.error()" (retry)="flights.reload()" />
      }
      <div class="table-wrap" [attr.aria-busy]="flights.isLoading()">
        <table>
          <caption class="visually-hidden">
            {{
              archived() ? 'Voos arquivados' : 'Voos ativos'
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">Ida</th>
              <th scope="col">Rota</th>
              <th scope="col">Companhia</th>
              <th scope="col">Passageiros</th>
              <th scope="col">Localizador</th>
              <th scope="col">Volta</th>
              <th scope="col"><span class="visually-hidden">Anexos</span></th>
            </tr>
          </thead>
          <tbody>
            @for (f of flights.value()?.content ?? []; track f.id) {
              <tr>
                <td class="num">
                  <a [routerLink]="['/voos-clientes', f.id]">{{ when(f.outboundAt) }}</a>
                </td>
                <td>{{ f.origin }} → {{ f.destination }}</td>
                <td>{{ f.airline }}</td>
                <td>{{ f.passengers.join(', ') }}</td>
                <td class="mono">{{ f.locator ?? '—' }}</td>
                <td class="num">{{ f.returnAt ? when(f.returnAt) : '—' }}</td>
                <td>
                  @if (f.attachmentCount > 0) {
                    <span [attr.aria-label]="f.attachmentCount + ' anexos'"
                      >📎 {{ f.attachmentCount }}</span
                    >
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="empty">
                  {{ flights.isLoading() ? 'Carregando…' : emptyText() }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="flights.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
  styles: `
    .more-filters {
      margin-bottom: var(--space-3);
    }
    .more-filters summary {
      cursor: pointer;
      font-weight: 600;
      margin-bottom: var(--space-2);
    }
  `,
})
export class ClientFlightsPage {
  private readonly api = inject(CrmApi);
  private readonly router = inject(Router);
  protected readonly session = inject(CrmSession);

  readonly q = input<string>('');
  readonly aba = input<string>('');
  readonly de = input<string>('');
  readonly ate = input<string>('');
  readonly cia = input<string>('');
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly archived = computed(() => this.aba() === 'arquivados');
  protected readonly flights = rxResource({
    params: () => ({
      q: this.q(),
      archived: this.archived(),
      from: this.de(),
      to: this.ate(),
      airline: this.cia(),
      page: this.pagina(),
    }),
    stream: ({ params }) => this.api.clientFlights({ ...params, size: 20 }),
  });
  protected readonly emptyText = computed(() =>
    this.q() ? 'Nenhum voo com esse passageiro ou localizador.' : 'Nenhum voo.',
  );

  constructor() {
    void this.session.ready();
  }

  protected when(value: string): string {
    return formatLocalDateTime(value);
  }

  protected search(event: Event, value: string): void {
    event.preventDefault();
    void this.router.navigate([], {
      queryParams: { q: value.trim() || null, pagina: null },
      queryParamsHandling: 'merge',
    });
  }

  protected filter(event: Event, from: string, to: string, airline: string): void {
    event.preventDefault();
    void this.router.navigate([], {
      queryParams: { de: from || null, ate: to || null, cia: airline.trim() || null, pagina: null },
      queryParamsHandling: 'merge',
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
