import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { BrandStore } from '../../core/crm/brand-store';
import { CrmSession } from '../../core/crm/crm-session';
import { errorMessage } from '../../core/errors/api-error';
import { NotificationService } from '../../core/notifications/notification.service';
import { PENDING_MODULES, PendingModule } from '../../layout/modules';
import { DashboardPanel } from './dashboard-panel';

/**
 * Visão geral do CRM: painel operacional (voos próximos, voos por mês, vouchers do mês, botão de olho), dados da
 * agência, situação das integrações e módulos ainda em construção. Sem faturas (JSON sem financeiro).
 */
@Component({
  selector: 'app-overview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DashboardPanel],
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">Visão geral</h1>
          <p>Olá, {{ auth.user()?.name }}. Este é o CRM interno da equipe Voe Familhas.</p>
        </div>
      </div>

      @if (session.loadFailed()) {
        <p class="alert" role="alert">
          Não conseguimos carregar seu perfil na API. Tente recarregar a página.
        </p>
      }

      <app-dashboard-panel />

      <div class="grid">
        <section class="card" aria-labelledby="empresa">
          <h2 id="empresa">Agência</h2>
          <div class="logo-box">
            @if (brand.logoUrl(); as logo) {
              <img [src]="logo" alt="Logo oficial da Voe Familhas" />
            } @else {
              <p class="muted">Logo oficial ainda não enviada: os posts usam a marca em texto.</p>
            }
            @if (session.can('crm:dashboard:criar')) {
              <label class="btn btn--small upload">
                {{ brand.logoUrl() ? 'Trocar logo oficial' : 'Enviar logo oficial' }}
                <input
                  type="file"
                  accept="image/png,image/webp"
                  class="visually-hidden"
                  (change)="uploadLogo($event)"
                />
              </label>
              <small class="muted"
                >PNG ou WEBP com fundo transparente, sem fundo azul e sem slogan.</small
              >
            }
          </div>
          @if (session.company(); as company) {
            <dl>
              <dt>Razão social</dt>
              <dd>{{ company.legalName }}</dd>
              <dt>CNPJ</dt>
              <dd class="num">{{ company.cnpj }}</dd>
              <dt>Marca</dt>
              <dd>{{ company.tradeName }}</dd>
              <dt>Site</dt>
              <dd>{{ company.site }}</dd>
              <dt>WhatsApp</dt>
              <dd class="num">{{ company.whatsapp }}</dd>
              <dt>E-mail</dt>
              <dd>{{ company.email || 'a definir' }}</dd>
              <dt>Instagram</dt>
              <dd>{{ company.instagram || 'a definir' }}</dd>
            </dl>
          } @else {
            <p class="muted">Carregando…</p>
          }
        </section>

        <section class="card" aria-labelledby="integracoes">
          <h2 id="integracoes">Integrações</h2>
          <p class="muted">
            Sem credencial, o recurso fica desligado e a tela oferece o preenchimento manual.
          </p>
          <ul class="list">
            @for (integration of session.integrations(); track integration.key) {
              <li>
                <span
                  class="badge"
                  [class.badge--ok]="integration.configured"
                  [class.badge--off]="!integration.configured"
                >
                  {{ integration.configured ? 'Configurada' : 'Não configurada' }}
                </span>
                <span>
                  <strong>{{ integration.label }}</strong>
                  @if (!integration.configured) {
                    <span class="muted detail">{{ integration.detail }}</span>
                  }
                </span>
              </li>
            }
          </ul>
        </section>
      </div>

      <section class="card" aria-labelledby="modulos">
        <h2 id="modulos">Módulos em construção</h2>
        <ul class="list">
          @for (module of modules; track module.title) {
            <li>
              <span class="badge" [class.badge--off]="module.blockedBy">
                {{ module.blockedBy ? 'Bloqueado' : 'Etapa ' + module.stage }}
              </span>
              <span>
                <strong>{{ module.title }}</strong>
                @if (module.blockedBy) {
                  <span class="muted detail">Aguarda {{ module.blockedBy }}.</span>
                }
              </span>
            </li>
          }
        </ul>
      </section>
    </section>
  `,
  styles: `
    .grid {
      display: grid;
      gap: var(--space-3);
      margin-bottom: var(--space-3);
    }
    @media (min-width: 60rem) {
      .grid {
        grid-template-columns: 1fr 1fr;
      }
    }
    h2 {
      margin: 0 0 var(--space-2);
      font-size: 1.125rem;
    }
    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: var(--space-1) var(--space-3);
      margin: 0;
    }
    dt {
      color: var(--color-muted);
    }
    dd {
      margin: 0;
      overflow-wrap: anywhere;
    }
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--space-2);
    }
    .list li {
      display: grid;
      grid-template-columns: 9rem 1fr;
      gap: var(--space-2);
      align-items: start;
    }
    .detail {
      display: block;
      font-size: 0.875rem;
    }
    .logo-box {
      display: grid;
      gap: var(--space-2);
      justify-items: start;
      margin-bottom: var(--space-3);
    }
    .logo-box img {
      max-width: 14rem;
      max-height: 6rem;
      background: var(--color-night);
      padding: var(--space-2);
      border-radius: var(--radius-sm);
    }
    .upload {
      cursor: pointer;
    }
    .badge {
      display: inline-block;
      padding: 0.125rem var(--space-2);
      border-radius: 999px;
      background: var(--color-neutral-bg);
      font-size: 0.8125rem;
      font-weight: 600;
      text-align: center;
    }
    .badge--ok {
      background: var(--color-success-bg);
      color: var(--color-success);
    }
    .badge--off {
      background: var(--color-warning-bg);
      color: var(--color-warning);
    }
  `,
})
export class OverviewPage {
  protected readonly auth = inject(AuthService);
  protected readonly session = inject(CrmSession);
  protected readonly brand = inject(BrandStore);
  private readonly notices = inject(NotificationService);
  protected readonly modules: readonly PendingModule[] = Object.values(PENDING_MODULES);

  constructor() {
    void this.session.ready();
    void this.brand.ready();
  }

  protected async uploadLogo(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      await this.brand.replace(file);
      this.notices.info('Logo oficial atualizada.');
    } catch (error) {
      this.notices.error(errorMessage(error));
    } finally {
      input.value = '';
    }
  }
}
