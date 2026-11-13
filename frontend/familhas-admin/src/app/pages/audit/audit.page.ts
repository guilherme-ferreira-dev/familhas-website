import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import type { AuditAction, AuditLog } from '../../core/api/api-types';
import { AdminApi } from '../../core/admin/admin-api';
import { formatDateTime } from '../../shared/format';
import { AUDIT_ACTION, options } from '../../shared/labels';
import { LoadError } from '../../shared/load-error';
import { Pager } from '../../shared/pager';

const RESOURCES: Record<string, string> = {
  flights: 'Voos',
  hotels: 'Hotéis',
  'room-offers': 'Quartos',
  'car-rental-offers': 'Carros',
  'travel-packages': 'Pacotes',
  cities: 'Cidades',
  categories: 'Categorias',
  clients: 'Clientes',
  ratings: 'Avaliações',
  quotes: 'Cotações',
  payments: 'Pagamentos',
  'pricing-settings': 'Margem e taxa',
};

/** Log de auditoria: toda escrita em /api/admin e toda exportação. Só leitura. */
@Component({
  selector: 'app-audit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Pager, LoadError],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Auditoria</h1>
          <p>
            Quem criou, alterou, excluiu ou exportou o quê. Inclui tentativas recusadas (status
            4xx).
          </p>
        </div>
      </div>

      <form
        class="filters"
        (submit)="
          apply($event, actorEl.value, resourceEl.value, actionEl.value, fromEl.value, toEl.value)
        "
      >
        <div class="field">
          <label for="quem">Quem</label>
          <input id="quem" #actorEl type="search" maxlength="100" [value]="quem() ?? ''" />
        </div>
        <div class="field">
          <label for="recurso">Recurso</label>
          <select id="recurso" #resourceEl>
            <option value="">Todos</option>
            @for (r of resourceOptions; track r.value) {
              <option [value]="r.value" [selected]="r.value === recurso()">{{ r.label }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="acao">Ação</label>
          <select id="acao" #actionEl>
            <option value="">Todas</option>
            @for (a of actionOptions; track a.value) {
              <option [value]="a.value" [selected]="a.value === acao()">{{ a.label }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="de">De</label>
          <input id="de" #fromEl type="date" [value]="de() ?? ''" />
        </div>
        <div class="field">
          <label for="ate">Até</label>
          <input id="ate" #toEl type="date" [value]="ate() ?? ''" />
        </div>
        <button type="submit" class="btn btn--primary">Filtrar</button>
      </form>

      @if (logs.error()) {
        <app-load-error [error]="logs.error()" (retry)="logs.reload()" />
      }
      <div class="table-wrap">
        <table>
          <caption class="visually-hidden">
            Log de auditoria
          </caption>
          <thead>
            <tr>
              <th scope="col">Quando</th>
              <th scope="col">Quem</th>
              <th scope="col">Ação</th>
              <th scope="col">Recurso</th>
              <th scope="col">Registro</th>
              <th scope="col" class="num">Status</th>
              <th scope="col">Detalhes</th>
            </tr>
          </thead>
          <tbody>
            @for (log of logs.value()?.content ?? []; track log.id) {
              <tr>
                <td>{{ dateTime(log.occurredAt) }}</td>
                <td>{{ log.actorUsername ?? log.actorSubject }}</td>
                <td>{{ action(log) }}</td>
                <td>{{ resource(log) }}</td>
                <td class="mono">{{ log.resourceId ?? '—' }}</td>
                <td class="num" [class.failed]="log.status >= 400">{{ log.status }}</td>
                <td class="mono">{{ log.details ?? '' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="empty">Nenhum registro.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [meta]="logs.value() ?? null" (pageChange)="goToPage($event)" />
    </section>
  `,
  styles: `
    .failed {
      color: var(--color-danger);
      font-weight: 600;
    }
  `,
})
export class AuditPage {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);

  readonly quem = input<string>();
  readonly recurso = input<string>();
  readonly acao = input<AuditAction | undefined>();
  readonly de = input<string>();
  readonly ate = input<string>();
  readonly pagina = input(0, { transform: (v: unknown) => Math.max(0, numberAttribute(v, 0)) });

  protected readonly actionOptions = options(AUDIT_ACTION);
  protected readonly resourceOptions = options(RESOURCES);
  protected readonly dateTime = formatDateTime;

  protected readonly logs = rxResource({
    params: () => ({
      actor: this.quem() || null,
      resource: this.recurso() || null,
      action: this.acao() || null,
      from: this.de() || null,
      to: this.ate() || null,
      page: this.pagina(),
    }),
    stream: ({ params }) => this.api.auditLogs({ ...params, size: 50 }),
  });

  protected action(log: AuditLog): string {
    return AUDIT_ACTION[log.action];
  }

  protected resource(log: AuditLog): string {
    return RESOURCES[log.resource] ?? log.resource;
  }

  protected apply(
    event: Event,
    quem: string,
    recurso: string,
    acao: string,
    de: string,
    ate: string,
  ): void {
    event.preventDefault();
    void this.router.navigate([], {
      queryParams: {
        quem: quem.trim() || null,
        recurso: recurso || null,
        acao: acao || null,
        de: de || null,
        ate: ate || null,
      },
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
