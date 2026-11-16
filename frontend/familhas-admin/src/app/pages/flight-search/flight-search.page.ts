import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import type {
  FlightSearchGroup,
  FlightSearchMode,
  FlightSearchRequest,
  FlightSearchResult,
  RecentFlightSearch,
} from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { formatDate, formatDateTime, money, todayInSaoPaulo } from '../../shared/format';

const IATA = /^[A-Z]{3}$/;

/** Destinos digitados ("mcz, rec ssa") em códigos IATA maiúsculos, sem repetição. */
export function parseDestinations(text: string): string[] {
  return [
    ...new Set(
      text
        .toUpperCase()
        .split(/[\s,;]+/)
        .map((code) => code.trim())
        .filter(Boolean),
    ),
  ];
}

/** Problemas do formulário antes de gastar consultas (a API confere de novo). */
export function validateSearch(request: FlightSearchRequest, today: string): string[] {
  const problems: string[] = [];
  if (request.destinations.length === 0) problems.push('Informe ao menos um destino.');
  if (request.destinations.length > 7) problems.push('No máximo 7 destinos por pesquisa.');
  if (request.destinations.some((d) => !IATA.test(d)))
    problems.push('Use códigos de aeroporto com 3 letras (ex.: MCZ).');
  if (request.destinations.includes(request.origin)) problems.push('Destino igual à origem.');
  if (request.mode === 'EXACT') {
    if (!request.departureDate || !request.returnDate) problems.push('Informe ida e volta.');
    else if (request.departureDate < today || request.returnDate < request.departureDate)
      problems.push('A ida precisa ser a partir de hoje e a volta depois da ida.');
  } else {
    const months = request.months ?? [];
    if (months.length === 0 || months.length > 3) problems.push('Escolha de 1 a 3 meses.');
    const stay = request.stayNights ?? 5;
    if (stay < 1 || stay > 30) problems.push('Estadia de 1 a 30 noites.');
  }
  return problems;
}

/**
 * Pesquisar Voos: cada destino cotado em separado (ida + volta somadas, por adulto), grupos de até 7 destinos,
 * datas exatas ou flexíveis por mês. Rodapé com as 5 últimas pesquisas e "Refazer".
 */
@Component({
  selector: 'app-flight-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Pesquisar Voos</h1>
          <p>Preços do Google Flights por destino. Valores de ida e volta por adulto.</p>
        </div>
      </div>

      @if (options.value(); as o) {
        @if (!o.configured) {
          <p class="alert alert--info" role="status">
            A pesquisa de voos (SerpApi) não está configurada no servidor. Os grupos podem ser
            montados, mas a cotação só funciona com a chave configurada.
          </p>
        }
      }

      <form class="card" (submit)="submit($event)" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="origem">Origem</label>
            <select id="origem" name="origem" [(ngModel)]="origin">
              @for (code of options.value()?.origins ?? []; track code) {
                <option [value]="code">{{ code }}</option>
              }
            </select>
          </div>
          <div class="field field--wide">
            <label for="destinos">Destinos (até 7, códigos de 3 letras)</label>
            <input
              id="destinos"
              name="destinos"
              [(ngModel)]="destinationsText"
              placeholder="MCZ, REC, SSA"
              autocomplete="off"
            />
          </div>
          @if (groups.value()?.length) {
            <div class="field">
              <label for="grupo">Usar grupo</label>
              <select id="grupo" name="grupo" (change)="useGroup($event)">
                <option value="">Escolher…</option>
                @for (g of groups.value(); track g.id) {
                  <option [value]="g.id">{{ g.name }} ({{ g.destinations.join(', ') }})</option>
                }
              </select>
            </div>
          }
          <fieldset class="field">
            <legend>Datas</legend>
            <label class="choice"
              ><input type="radio" name="modo" value="EXACT" [(ngModel)]="mode" /> Exatas</label
            >
            <label class="choice"
              ><input type="radio" name="modo" value="FLEXIBLE" [(ngModel)]="mode" /> Flexíveis por
              mês</label
            >
          </fieldset>
          @if (mode === 'EXACT') {
            <div class="field">
              <label for="ida">Ida</label>
              <input id="ida" name="ida" type="date" [min]="today" [(ngModel)]="departureDate" />
            </div>
            <div class="field">
              <label for="volta">Volta</label>
              <input
                id="volta"
                name="volta"
                type="date"
                [min]="departureDate || today"
                [(ngModel)]="returnDate"
              />
            </div>
          } @else {
            @for (i of [0, 1, 2]; track i) {
              <div class="field">
                <label [for]="'mes-' + i">{{ i === 0 ? 'Mês' : 'Outro mês (opcional)' }}</label>
                <input
                  [id]="'mes-' + i"
                  [name]="'mes-' + i"
                  type="month"
                  [min]="today.slice(0, 7)"
                  [(ngModel)]="months[i]"
                />
              </div>
            }
            <div class="field">
              <label for="noites">Estadia (noites)</label>
              <input
                id="noites"
                name="noites"
                type="number"
                min="1"
                max="30"
                [(ngModel)]="stayNights"
              />
            </div>
          }
          <div class="field">
            <label for="adultos">Adultos</label>
            <input id="adultos" name="adultos" type="number" min="1" max="9" [(ngModel)]="adults" />
          </div>
        </div>
        @if (problems().length) {
          <ul class="alert" role="alert">
            @for (p of problems(); track p) {
              <li>{{ p }}</li>
            }
          </ul>
        }
        <div class="actions">
          <button
            type="submit"
            class="btn btn--primary"
            [disabled]="searching() || !session.can('crm:pesquisa-voos:criar')"
          >
            {{ searching() ? 'Pesquisando…' : 'Pesquisar' }}
          </button>
          @if (session.can('crm:pesquisa-voos:criar')) {
            <button type="button" class="btn" (click)="saveGroup()">
              Salvar destinos como grupo
            </button>
          }
        </div>
      </form>

      @if (result(); as r) {
        <section aria-labelledby="resultados" class="results">
          <h2 id="resultados">Resultados</h2>
          @for (d of r.destinations; track d.destination) {
            <section class="card" [attr.aria-labelledby]="'dest-' + d.destination">
              <h3 [id]="'dest-' + d.destination">{{ r.request.origin }} → {{ d.destination }}</h3>
              @if (d.error) {
                <p class="alert" role="status">{{ d.error }}</p>
              }
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Ida</th>
                      <th scope="col">Volta</th>
                      <th scope="col" class="num">Ida + volta</th>
                      <th scope="col">Companhias</th>
                      <th scope="col">Paradas</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (o of d.offers.slice(0, 10); track $index) {
                      <tr>
                        <td class="num">{{ date(o.departureDate) }}</td>
                        <td class="num">{{ date(o.returnDate) }}</td>
                        <td class="num">
                          <strong>{{ price(o.price, o.currency) }}</strong>
                        </td>
                        <td>{{ o.airlines.join(', ') || '—' }}</td>
                        <td>{{ o.stops === 0 ? 'Direto' : o.stops }}</td>
                      </tr>
                    } @empty {
                      <tr>
                        <td colspan="5" class="empty">Nenhum voo encontrado.</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </section>
          }
        </section>
      }

      @if (groups.value()?.length && session.can('crm:pesquisa-voos:excluir')) {
        <details class="card">
          <summary>Grupos de destinos</summary>
          <ul class="plain">
            @for (g of groups.value(); track g.id) {
              <li>
                <strong>{{ g.name }}</strong>: {{ g.destinations.join(', ') }}
                <button type="button" class="btn btn--small" (click)="deleteGroup(g)">
                  Excluir
                </button>
              </li>
            }
          </ul>
        </details>
      }

      <footer class="recent" aria-labelledby="recentes">
        <h2 id="recentes">Últimas pesquisas</h2>
        <ul class="plain">
          @for (s of recent.value() ?? []; track s.id) {
            <li>
              <span>{{ s.summary }}</span>
              <span class="muted">{{ dateTime(s.lastRunAt) }} · {{ s.runBy ?? '—' }}</span>
              <button type="button" class="btn btn--small" (click)="redo(s)">Refazer</button>
            </li>
          } @empty {
            <li class="muted">Nenhuma pesquisa ainda.</li>
          }
        </ul>
      </footer>
    </section>
  `,
  styles: `
    .choice {
      display: inline-flex;
      gap: var(--space-1);
      align-items: center;
      min-height: 2.75rem;
      margin-inline-end: var(--space-3);
    }
    fieldset.field {
      border: 0;
      padding: 0;
      margin: 0;
    }
    .results {
      display: grid;
      gap: var(--space-3);
      margin: var(--space-4) 0;
    }
    h2,
    h3 {
      margin: 0 0 var(--space-2);
    }
    .plain {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: var(--space-2);
    }
    .recent li {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: center;
    }
    .recent {
      margin-top: var(--space-4);
      border-top: 1px solid var(--color-border);
      padding-top: var(--space-3);
    }
  `,
})
export class FlightSearchPage {
  private readonly api = inject(CrmApi);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  protected readonly today = todayInSaoPaulo();
  protected origin = '';
  protected destinationsText = '';
  protected mode: FlightSearchMode = 'EXACT';
  protected departureDate = '';
  protected returnDate = '';
  protected months: string[] = ['', '', ''];
  protected stayNights = 5;
  protected adults = 1;

  protected readonly searching = signal(false);
  protected readonly problems = signal<string[]>([]);
  protected readonly result = signal<FlightSearchResult | null>(null);

  protected readonly options = rxResource({ stream: () => this.api.flightSearchOptions() });
  protected readonly groups = rxResource({ stream: () => this.api.flightSearchGroups() });
  protected readonly recent = rxResource({ stream: () => this.api.recentFlightSearches() });
  private readonly defaultOrigin = computed(() => this.options.value()?.origins[0] ?? '');

  constructor() {
    void this.session.ready();
  }

  protected date(value: string): string {
    return formatDate(value);
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected price(value: number, currency: string): string {
    return money(value, currency);
  }

  protected useGroup(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    const group = this.groups.value()?.find((g) => g.id === id);
    if (group) this.destinationsText = group.destinations.join(', ');
  }

  protected request(): FlightSearchRequest {
    const flexible = this.mode === 'FLEXIBLE';
    return {
      origin: this.origin || this.defaultOrigin(),
      destinations: parseDestinations(this.destinationsText),
      mode: this.mode,
      departureDate: flexible ? null : this.departureDate || null,
      returnDate: flexible ? null : this.returnDate || null,
      months: flexible ? [...new Set(this.months.filter(Boolean))] : null,
      stayNights: flexible ? Number(this.stayNights) : null,
      adults: Number(this.adults) || 1,
    };
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    const request = this.request();
    const problems = validateSearch(request, this.today);
    this.problems.set(problems);
    if (problems.length) return;
    this.searching.set(true);
    try {
      this.result.set(await firstValueFrom(this.api.searchFlights(request)));
      this.recent.reload();
    } catch (error) {
      this.problems.set([errorMessage(error)]);
    } finally {
      this.searching.set(false);
    }
  }

  protected redo(search: RecentFlightSearch): Promise<void> {
    const r = search.request;
    this.origin = r.origin;
    this.destinationsText = r.destinations.join(', ');
    this.mode = r.mode;
    this.departureDate = r.departureDate ?? '';
    this.returnDate = r.returnDate ?? '';
    this.months = [...(r.months ?? []), '', '', ''].slice(0, 3);
    this.stayNights = r.stayNights ?? 5;
    this.adults = r.adults ?? 1;
    return this.submit(new Event('submit'));
  }

  protected async saveGroup(): Promise<void> {
    const destinations = parseDestinations(this.destinationsText);
    const problems = validateSearch(
      { origin: '', destinations, mode: 'FLEXIBLE', months: ['x'] },
      this.today,
    ).filter((p) => !p.includes('igual'));
    if (problems.length) {
      this.problems.set(problems);
      return;
    }
    const name = prompt('Nome do grupo (ex.: Nordeste)')?.trim();
    if (!name) return;
    try {
      await firstValueFrom(this.api.createFlightSearchGroup({ name, destinations }));
      this.groups.reload();
      this.notices.info('Grupo salvo.');
    } catch (error) {
      this.notices.error(errorMessage(error));
    }
  }

  protected async deleteGroup(group: FlightSearchGroup): Promise<void> {
    if (!confirm(`Excluir o grupo ${group.name}?`)) return;
    try {
      await firstValueFrom(this.api.deleteFlightSearchGroup(group.id));
      this.groups.reload();
    } catch (error) {
      this.notices.error(errorMessage(error));
    }
  }
}
