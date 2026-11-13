import { computed, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { MyTripsApi } from './my-trips-api';

/**
 * Ids dos pacotes favoritos do cliente logado, compartilhados entre o botão de favoritar (cartões e detalhe)
 * e a página "Minhas viagens". Carrega uma vez quando a sessão começa e esvazia no logout.
 */
@Injectable({ providedIn: 'root' })
export class FavoritesStore {
  private readonly api = inject(MyTripsApi);
  private readonly auth = inject(AuthService);
  private readonly ids = signal<ReadonlySet<string>>(new Set());
  private readonly loaded = signal(false);
  /** Cliques feitos enquanto a lista carregava: valem sobre a resposta, que pode ter saído antes deles. */
  private readonly pending = new Map<string, boolean>();

  readonly ready = this.loaded.asReadonly();
  readonly count = computed(() => this.ids().size);

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        untracked(() => this.refresh());
      } else {
        this.ids.set(new Set());
        this.loaded.set(false);
      }
    });
  }

  has(packageId: string): boolean {
    return this.ids().has(packageId);
  }

  /** Recarrega do servidor; erro não quebra a tela (o botão só começa desmarcado). */
  refresh(): void {
    this.pending.clear();
    this.api.favorites().subscribe({
      next: (list) => {
        const ids = new Set(list.map((p) => p.id));
        for (const [id, favorite] of this.pending) {
          if (favorite) ids.add(id);
          else ids.delete(id);
        }
        this.pending.clear();
        this.ids.set(ids);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  /** Troca o estado já na tela e desfaz se a API recusar. PUT e DELETE são idempotentes. */
  toggle(packageId: string): Observable<void> {
    const wasFavorite = this.has(packageId);
    this.update(packageId, !wasFavorite);
    const request = wasFavorite
      ? this.api.removeFavorite(packageId)
      : this.api.addFavorite(packageId);
    return request.pipe(tap({ error: () => this.update(packageId, wasFavorite) }));
  }

  private update(packageId: string, favorite: boolean): void {
    if (!this.loaded()) this.pending.set(packageId, favorite);
    this.ids.update((current) => {
      const next = new Set(current);
      if (favorite) next.add(packageId);
      else next.delete(packageId);
      return next;
    });
  }
}
