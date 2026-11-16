import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import type {
  AnonymizationReport,
  Incident,
  IncidentRequest,
  SubjectReport,
  SubjectRequest,
} from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { formatDateTime } from '../../shared/format';

type Severity = IncidentRequest['severity'];
type Status = IncidentRequest['status'];

/** Campos de data do formulário (datetime-local, horário do navegador). */
interface IncidentForm {
  title: string;
  description: string;
  severity: Severity;
  status: Status;
  personalData: boolean;
  dataCategories: string;
  affectedEstimate: number | null;
  actionsTaken: string;
  detectedAt: string;
  containedAt: string;
  closedAt: string;
  anpdNotifiedAt: string;
  holdersNotifiedAt: string;
}

/** Converte ISO para o valor do datetime-local (horário local) e de volta. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(value: string): string | null {
  return value ? new Date(value).toISOString() : null;
}

function emptyForm(): IncidentForm {
  return {
    title: '',
    description: '',
    severity: 'MEDIA',
    status: 'ABERTO',
    personalData: false,
    dataCategories: '',
    affectedEstimate: null,
    actionsTaken: '',
    detectedAt: toLocalInput(new Date().toISOString()),
    containedAt: '',
    closedAt: '',
    anpdNotifiedAt: '',
    holdersNotifiedAt: '',
  };
}

/**
 * Privacidade (LGPD; ADR 0018). Só admin. Pedidos de titular (relatório e anonimização com prévia) e o registro de
 * incidentes do plano de resposta (docs/incident-response.md). O CPF digitado nunca vai para a URL.
 */
@Component({
  selector: 'app-privacy-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Privacidade (LGPD)</h1>
          <p>
            Pedidos de titulares (acesso e eliminação) e registro de incidentes. Tudo vai para a
            auditoria, com o CPF mascarado.
          </p>
        </div>
      </div>

      <section class="card" aria-labelledby="titular">
        <h2 id="titular">Pedido de titular</h2>
        <div class="row">
          <div class="field">
            <label for="cpf">CPF do titular</label>
            <input
              id="cpf"
              [(ngModel)]="cpf"
              maxlength="14"
              inputmode="numeric"
              autocomplete="off"
            />
          </div>
          <div class="field">
            <label for="ref">Protocolo ou canal do pedido</label>
            <input id="ref" [(ngModel)]="reference" maxlength="120" />
          </div>
        </div>
        <label class="check">
          <input type="checkbox" [(ngModel)]="identityVerified" />
          Confirmei que quem pede é o titular (documento ou canal já conhecido)
        </label>
        <div class="actions">
          <button
            type="button"
            class="btn btn--primary"
            [disabled]="busy() || !canSubmit()"
            (click)="report()"
          >
            Gerar relatório
          </button>
          <button type="button" class="btn" [disabled]="busy() || !canSubmit()" (click)="preview()">
            Prévia da anonimização
          </button>
        </div>

        @if (subject(); as r) {
          <h3>Relatório: {{ r.flights.length }} voos, {{ r.vouchers.length }} vouchers</h3>
          <p class="muted">
            O arquivo baixado tem os dados como estão, para entregar ao próprio titular.
          </p>
        }

        @if (anonymization(); as a) {
          <h3>{{ a.applied ? 'Anonimização feita' : 'Prévia (nada foi alterado ainda)' }}</h3>
          <p>
            {{ a.travelersAnonymized }} passageiros e {{ a.vouchersAnonymized }} vouchers
            {{ a.applied ? 'anonimizados' : 'serão anonimizados' }}. Nome vira "Titular
            anonimizado"; CPF, telefone e observações saem.
          </p>
          @if (a.kept.length) {
            <p>Ficam como estão:</p>
            <ul>
              @for (k of a.kept; track $index) {
                <li>
                  {{ k.kind }} {{ k.reference }}: {{ k.reason
                  }}{{ k.until ? ' (até ' + k.until + ')' : '' }}
                </li>
              }
            </ul>
          }
          @if (!a.applied) {
            <button
              type="button"
              class="btn btn--danger"
              [disabled]="busy() || a.travelersAnonymized + a.vouchersAnonymized === 0"
              (click)="anonymize()"
            >
              Anonimizar
            </button>
          }
        }
      </section>

      <section class="card" aria-labelledby="incidentes">
        <h2 id="incidentes">Incidentes</h2>
        <p class="muted">
          Siga o plano de resposta: conter, registrar, avaliar o risco e, se houver risco relevante
          aos titulares, comunicar a ANPD e os titulares. Não escreva dados pessoais aqui.
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Detectado</th>
                <th scope="col">Título</th>
                <th scope="col">Gravidade</th>
                <th scope="col">Situação</th>
                <th scope="col">Dado pessoal</th>
                <th scope="col"><span class="visually-hidden">Ações</span></th>
              </tr>
            </thead>
            <tbody>
              @for (i of incidents.value() ?? []; track i.id) {
                <tr>
                  <td>{{ dateTime(i.detectedAt) }}</td>
                  <td>{{ i.title }}</td>
                  <td>{{ i.severity }}</td>
                  <td>{{ i.status }}</td>
                  <td>{{ i.personalData ? 'Sim' : 'Não' }}</td>
                  <td>
                    <button type="button" class="btn btn--small" (click)="edit(i)">Editar</button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="muted">Nenhum incidente registrado.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <h3>{{ editing() ? 'Editar incidente' : 'Registrar incidente' }}</h3>
        <form class="incident" (ngSubmit)="saveIncident()">
          <div class="field">
            <label for="i-title">Título</label>
            <input id="i-title" name="title" [(ngModel)]="form.title" maxlength="150" required />
          </div>
          <div class="field">
            <label for="i-desc">O que aconteceu</label>
            <textarea
              id="i-desc"
              name="description"
              rows="3"
              [(ngModel)]="form.description"
              maxlength="4000"
              required
            ></textarea>
          </div>
          <div class="row">
            <div class="field">
              <label for="i-sev">Gravidade</label>
              <select id="i-sev" name="severity" [(ngModel)]="form.severity">
                <option value="BAIXA">Baixa</option>
                <option value="MEDIA">Média</option>
                <option value="ALTA">Alta</option>
              </select>
            </div>
            <div class="field">
              <label for="i-status">Situação</label>
              <select id="i-status" name="status" [(ngModel)]="form.status">
                <option value="ABERTO">Aberto</option>
                <option value="CONTIDO">Contido</option>
                <option value="ENCERRADO">Encerrado</option>
              </select>
            </div>
          </div>
          <label class="check">
            <input type="checkbox" name="personalData" [(ngModel)]="form.personalData" />
            Envolveu dado pessoal
          </label>
          @if (form.personalData) {
            <div class="row">
              <div class="field">
                <label for="i-cat">Categorias de dados (ex.: nome, CPF)</label>
                <input
                  id="i-cat"
                  name="dataCategories"
                  [(ngModel)]="form.dataCategories"
                  maxlength="300"
                />
              </div>
              <div class="field">
                <label for="i-aff">Titulares afetados (estimativa)</label>
                <input
                  id="i-aff"
                  name="affectedEstimate"
                  type="number"
                  min="0"
                  [(ngModel)]="form.affectedEstimate"
                />
              </div>
            </div>
          }
          <div class="field">
            <label for="i-act">Medidas tomadas</label>
            <textarea
              id="i-act"
              name="actionsTaken"
              rows="2"
              [(ngModel)]="form.actionsTaken"
              maxlength="4000"
            ></textarea>
          </div>
          <div class="row">
            <div class="field">
              <label for="i-det">Detectado em</label>
              <input
                id="i-det"
                name="detectedAt"
                type="datetime-local"
                [(ngModel)]="form.detectedAt"
                required
              />
            </div>
            <div class="field">
              <label for="i-con">Contido em</label>
              <input
                id="i-con"
                name="containedAt"
                type="datetime-local"
                [(ngModel)]="form.containedAt"
              />
            </div>
            <div class="field">
              <label for="i-clo">Encerrado em</label>
              <input id="i-clo" name="closedAt" type="datetime-local" [(ngModel)]="form.closedAt" />
            </div>
          </div>
          @if (form.personalData) {
            <div class="row">
              <div class="field">
                <label for="i-anpd">ANPD comunicada em</label>
                <input
                  id="i-anpd"
                  name="anpdNotifiedAt"
                  type="datetime-local"
                  [(ngModel)]="form.anpdNotifiedAt"
                />
              </div>
              <div class="field">
                <label for="i-hold">Titulares comunicados em</label>
                <input
                  id="i-hold"
                  name="holdersNotifiedAt"
                  type="datetime-local"
                  [(ngModel)]="form.holdersNotifiedAt"
                />
              </div>
            </div>
          }
          <div class="actions">
            <button type="submit" class="btn btn--primary" [disabled]="busy()">Salvar</button>
            @if (editing()) {
              <button type="button" class="btn" (click)="cancelEdit()">Cancelar</button>
            }
          </div>
        </form>
      </section>
    </section>
  `,
  styles: `
    .card {
      display: grid;
      gap: var(--space-2);
      margin-bottom: var(--space-3);
    }
    h2,
    h3 {
      margin: 0;
      font-size: 1.125rem;
    }
    .row {
      display: grid;
      gap: var(--space-2);
    }
    @media (min-width: 48rem) {
      .row {
        grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      }
    }
    .check {
      display: flex;
      gap: var(--space-2);
      align-items: center;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }
    .incident {
      display: grid;
      gap: var(--space-2);
    }
  `,
})
export class PrivacyPage {
  private readonly api = inject(CrmApi);
  private readonly notices = inject(NotificationService);
  protected readonly session = inject(CrmSession);

  protected readonly busy = signal(false);
  protected readonly subject = signal<SubjectReport | null>(null);
  protected readonly anonymization = signal<AnonymizationReport | null>(null);
  protected readonly incidents = rxResource({ stream: () => this.api.incidents() });
  protected readonly editing = signal<Incident | null>(null);

  protected cpf = '';
  protected reference = '';
  protected identityVerified = false;
  protected form: IncidentForm = emptyForm();

  protected canSubmit(): boolean {
    return (
      this.cpf.replace(/\D/g, '').length === 11 && !!this.reference.trim() && this.identityVerified
    );
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  private request(): SubjectRequest {
    return {
      cpf: this.cpf,
      identityVerified: this.identityVerified,
      reference: this.reference.trim(),
    };
  }

  protected report(): Promise<void> {
    return this.run(async () => {
      this.anonymization.set(null);
      const result = await firstValueFrom(this.api.subjectReport(this.request()));
      this.subject.set(result);
      const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `relatorio-titular-${this.reference.trim().replace(/[^\w-]+/g, '_')}.json`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  protected preview(): Promise<void> {
    return this.run(async () => {
      this.subject.set(null);
      this.anonymization.set(
        await firstValueFrom(this.api.anonymizeSubject(this.request(), false)),
      );
    });
  }

  protected async anonymize(): Promise<void> {
    const a = this.anonymization();
    if (!a || !confirm('Anonimizar os dados do titular? Não dá para desfazer.')) return;
    await this.run(async () => {
      this.anonymization.set(await firstValueFrom(this.api.anonymizeSubject(this.request(), true)));
      this.notices.info('Titular anonimizado.');
    });
  }

  protected edit(incident: Incident): void {
    this.editing.set(incident);
    this.form = {
      title: incident.title,
      description: incident.description,
      severity: incident.severity,
      status: incident.status,
      personalData: incident.personalData,
      dataCategories: incident.dataCategories ?? '',
      affectedEstimate: incident.affectedEstimate ?? null,
      actionsTaken: incident.actionsTaken ?? '',
      detectedAt: toLocalInput(incident.detectedAt),
      containedAt: toLocalInput(incident.containedAt),
      closedAt: toLocalInput(incident.closedAt),
      anpdNotifiedAt: toLocalInput(incident.anpdNotifiedAt),
      holdersNotifiedAt: toLocalInput(incident.holdersNotifiedAt),
    };
  }

  protected cancelEdit(): void {
    this.editing.set(null);
    this.form = emptyForm();
  }

  protected saveIncident(): Promise<void> {
    const f = this.form;
    const current = this.editing();
    const body: IncidentRequest = {
      title: f.title,
      description: f.description,
      severity: f.severity,
      status: f.status,
      personalData: f.personalData,
      dataCategories: f.personalData ? f.dataCategories || null : null,
      affectedEstimate: f.personalData ? f.affectedEstimate : null,
      actionsTaken: f.actionsTaken || null,
      detectedAt: fromLocalInput(f.detectedAt) ?? '',
      containedAt: fromLocalInput(f.containedAt),
      closedAt: fromLocalInput(f.closedAt),
      anpdNotifiedAt: f.personalData ? fromLocalInput(f.anpdNotifiedAt) : null,
      holdersNotifiedAt: f.personalData ? fromLocalInput(f.holdersNotifiedAt) : null,
      version: current?.version ?? null,
    };
    return this.run(async () => {
      await firstValueFrom(this.api.saveIncident(current?.id ?? null, body));
      this.notices.info('Incidente salvo.');
      this.cancelEdit();
      this.incidents.reload();
    });
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
}
