import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import type { CrmRole, TeamMember } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { formatDateTime } from '../../shared/format';
import { StatusBadge } from '../../shared/status-badge';

export const ROLE_LABEL: Record<CrmRole, string> = {
  ADMIN: 'Administrador',
  GESTOR: 'Gestor',
  ATENDENTE: 'Atendente',
  MARKETING: 'Marketing',
};

/** O que falta a pessoa fazer no primeiro acesso, em português. */
export function pendingLabel(actions: readonly string[]): string {
  const labels: Record<string, string> = {
    UPDATE_PASSWORD: 'definir a senha',
    CONFIGURE_TOTP: 'cadastrar o TOTP',
    VERIFY_EMAIL: 'confirmar o e-mail',
  };
  const items = actions.map((a) => labels[a] ?? a.toLowerCase());
  return items.length ? `Falta ${items.join(' e ')}` : '';
}

/**
 * Equipe (JSON: somente administrador). Convite pelo e-mail do Keycloak, troca de papel, desativar/reativar e
 * reenviar o acesso. A API impede alterar a própria conta e tirar o último admin.
 */
@Component({
  selector: 'app-team-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, StatusBadge],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Equipe</h1>
          <p>Quem acessa o CRM e com qual papel. Administradores entram com TOTP.</p>
        </div>
      </div>

      @if (members.error()) {
        <p class="alert" role="alert">{{ message(members.error()) }}</p>
      }

      <form class="card invite" (submit)="invite($event)">
        <h2>Convidar</h2>
        <div class="form-grid">
          <div class="field">
            <label for="email">E-mail *</label>
            <input
              id="email"
              name="email"
              type="email"
              [(ngModel)]="email"
              maxlength="180"
              required
              autocomplete="off"
            />
          </div>
          <div class="field">
            <label for="nome">Nome *</label>
            <input id="nome" name="nome" [(ngModel)]="firstName" maxlength="80" required />
          </div>
          <div class="field">
            <label for="sobrenome">Sobrenome *</label>
            <input id="sobrenome" name="sobrenome" [(ngModel)]="lastName" maxlength="80" required />
          </div>
          <div class="field">
            <label for="papel">Papel</label>
            <select id="papel" name="papel" [(ngModel)]="role">
              @for (r of roles; track r) {
                <option [value]="r">{{ roleLabel[r] }}</option>
              }
            </select>
          </div>
        </div>
        <button type="submit" class="btn btn--primary" [disabled]="busy()">Enviar convite</button>
        <small class="muted"
          >A pessoa recebe um e-mail do Keycloak para definir a senha. Não há cadastro
          público.</small
        >
      </form>

      <div class="table-wrap" [attr.aria-busy]="members.isLoading()">
        <table>
          <caption class="visually-hidden">
            Membros da equipe
          </caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">E-mail</th>
              <th scope="col">Papel</th>
              <th scope="col">Situação</th>
              <th scope="col"><span class="visually-hidden">Ações</span></th>
            </tr>
          </thead>
          <tbody>
            @for (m of members.value() ?? []; track m.id) {
              <tr>
                <td>
                  {{ m.firstName }} {{ m.lastName }}
                  @if (m.self) {
                    <span class="muted">(você)</span>
                  }
                </td>
                <td>{{ m.email }}</td>
                <td>
                  @if (m.self) {
                    {{ roleLabel[m.role] }}
                  } @else {
                    <select
                      [attr.aria-label]="'Papel de ' + m.firstName"
                      [ngModel]="m.role"
                      (ngModelChange)="changeRole(m, $event)"
                      [disabled]="busy()"
                    >
                      @for (r of roles; track r) {
                        <option [value]="r">{{ roleLabel[r] }}</option>
                      }
                    </select>
                  }
                </td>
                <td>
                  <app-status-badge
                    [label]="m.enabled ? 'Ativo' : 'Desativado'"
                    [tone]="m.enabled ? 'success' : 'neutral'"
                  />
                  @if (m.role === 'ADMIN') {
                    <span class="muted"> · TOTP {{ m.totpConfigured ? 'ok' : 'pendente' }}</span>
                  }
                  @if (pending(m); as p) {
                    <div class="muted small">{{ p }}</div>
                  }
                  @if (m.createdAt) {
                    <div class="muted small">Desde {{ dateTime(m.createdAt) }}</div>
                  }
                </td>
                <td class="actions">
                  @if (!m.self) {
                    @if (m.enabled) {
                      <button
                        type="button"
                        class="btn btn--small"
                        [disabled]="busy()"
                        (click)="act(m, 'resend')"
                      >
                        Reenviar acesso
                      </button>
                      <button
                        type="button"
                        class="btn btn--small btn--danger"
                        [disabled]="busy()"
                        (click)="act(m, 'disable')"
                      >
                        Desativar
                      </button>
                    } @else {
                      <button
                        type="button"
                        class="btn btn--small"
                        [disabled]="busy()"
                        (click)="act(m, 'enable')"
                      >
                        Reativar
                      </button>
                    }
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="empty">
                  {{ members.isLoading() ? 'Carregando…' : 'Nenhum membro.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .invite {
      display: grid;
      gap: var(--space-2);
      margin-bottom: var(--space-4);
    }
    h2 {
      margin: 0;
      font-size: 1.125rem;
    }
    .small {
      font-size: 0.8125rem;
    }
  `,
})
export class TeamPage {
  private readonly api = inject(CrmApi);
  private readonly notices = inject(NotificationService);
  protected readonly roleLabel = ROLE_LABEL;
  protected readonly roles = Object.keys(ROLE_LABEL) as CrmRole[];
  protected readonly busy = signal(false);
  protected readonly members = rxResource({ stream: () => this.api.team() });

  protected email = '';
  protected firstName = '';
  protected lastName = '';
  protected role: CrmRole = 'ATENDENTE';

  protected message(error: unknown): string {
    return errorMessage(error);
  }

  protected pending(m: TeamMember): string {
    return pendingLabel(m.pendingActions);
  }

  protected dateTime(value: string): string {
    return formatDateTime(value);
  }

  protected async invite(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.email.trim() || !this.firstName.trim() || !this.lastName.trim()) {
      this.notices.error('Preencha e-mail, nome e sobrenome.');
      return;
    }
    await this.run(async () => {
      const member = await firstValueFrom(
        this.api.invite({
          email: this.email.trim(),
          firstName: this.firstName.trim(),
          lastName: this.lastName.trim(),
          role: this.role,
        }),
      );
      this.notices.info(`Convite enviado para ${member.email}.`);
      this.email = this.firstName = this.lastName = '';
    });
  }

  protected async changeRole(m: TeamMember, role: CrmRole): Promise<void> {
    if (
      !confirm(
        `Trocar o papel de ${m.firstName} para ${ROLE_LABEL[role]}? As sessões abertas serão encerradas.`,
      )
    ) {
      this.members.reload();
      return;
    }
    await this.run(async () => {
      await firstValueFrom(this.api.changeRole(m.id, role));
      this.notices.info('Papel atualizado.');
    });
  }

  protected async act(m: TeamMember, action: 'disable' | 'enable' | 'resend'): Promise<void> {
    if (
      action === 'disable' &&
      !confirm(`Desativar ${m.firstName}? As sessões abertas serão encerradas.`)
    )
      return;
    await this.run(async () => {
      await firstValueFrom(this.api.memberAction(m.id, action));
      this.notices.info(
        action === 'resend'
          ? 'Acesso reenviado por e-mail.'
          : action === 'disable'
            ? 'Membro desativado.'
            : 'Membro reativado.',
      );
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
      this.members.reload();
    }
  }
}
