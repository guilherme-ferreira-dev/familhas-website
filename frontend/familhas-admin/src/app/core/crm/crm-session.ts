import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Company, CrmMe, CrmSettings, IntegrationStatus } from '../api/crm-types';
import { APP_CONFIG } from '../config/app-config';

/**
 * Perfil, permissões, dados da agência e integrações, carregados uma vez por sessão (`/api/crm/me` e
 * `/api/crm/settings`). O logout chama {@link clear}: nada de um usuário sobra para o próximo.
 */
@Injectable({ providedIn: 'root' })
export class CrmSession {
  private readonly http = inject(HttpClient);
  private readonly api = inject(APP_CONFIG).apiBaseUrl;
  private readonly me = signal<CrmMe | null>(null);
  private readonly settings = signal<CrmSettings | null>(null);
  private readonly failed = signal(false);
  private loading: Promise<void> | null = null;

  readonly profile = this.me.asReadonly();
  readonly loadFailed = this.failed.asReadonly();
  readonly company = computed<Company | null>(() => this.settings()?.company ?? null);
  readonly integrations = computed<readonly IntegrationStatus[]>(
    () => this.settings()?.integrations ?? [],
  );
  private readonly permissions = computed(() => new Set(this.me()?.permissions ?? []));

  /** Carrega uma vez; chamadas seguintes reaproveitam a mesma promessa. */
  ready(): Promise<void> {
    this.loading ??= this.load();
    return this.loading;
  }

  can(permission: string): boolean {
    return this.permissions().has(permission);
  }

  clear(): void {
    this.me.set(null);
    this.settings.set(null);
    this.failed.set(false);
    this.loading = null;
  }

  private async load(): Promise<void> {
    try {
      const me = await firstValueFrom(this.http.get<CrmMe>(`${this.api}/api/crm/me`));
      this.me.set(me);
      if (me.permissions.includes('crm:dashboard:ler')) {
        this.settings.set(
          await firstValueFrom(this.http.get<CrmSettings>(`${this.api}/api/crm/settings`)),
        );
      }
    } catch {
      // Sem perfil, o menu fica vazio e as rotas com permissão mandam para "sem permissão".
      this.failed.set(true);
      this.loading = null;
    }
  }
}
