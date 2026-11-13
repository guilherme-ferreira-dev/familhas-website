import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { ApiError, errorMessage } from '../core/errors/api-error';
import { FavoritesStore } from '../core/my-trips/favorites-store';
import { NotificationService } from '../core/notifications/notification.service';

/**
 * Botão de favoritar pacote (coração). Sem sessão, leva para o login e volta para a mesma tela.
 * `aria-pressed` diz ao leitor de tela se o pacote já está nos favoritos.
 */
@Component({
  selector: 'app-favorite-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="favorite"
      [class.favorite--on]="active()"
      [class.favorite--compact]="compact()"
      [attr.aria-pressed]="active()"
      [attr.aria-label]="compact() ? label() : null"
      [disabled]="busy()"
      (click)="toggle()"
    >
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
        <path
          d="M12 20.5s-7.5-4.6-9.3-9.2C1.4 8 3.3 4.5 6.9 4.5c2.1 0 3.6 1.2 5.1 3 1.5-1.8 3-3 5.1-3 3.6 0 5.5 3.5 4.2 6.8-1.8 4.6-9.3 9.2-9.3 9.2Z"
        />
      </svg>
      @if (!compact()) {
        <span>{{ active() ? 'Nos favoritos' : 'Favoritar' }}</span>
      }
    </button>
  `,
  styles: `
    .favorite {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      min-height: var(--tap);
      padding-inline: var(--space-3);
      border: 2px solid var(--color-border);
      border-radius: 999px;
      background: var(--color-surface);
      color: var(--color-text);
      font: inherit;
      font-weight: 600;
      cursor: pointer;
    }
    .favorite svg {
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
    }
    .favorite--on {
      border-color: var(--color-accent);
      color: var(--color-accent);
    }
    .favorite--on svg {
      fill: currentColor;
    }
    .favorite--compact {
      min-width: var(--tap);
      justify-content: center;
      padding: 0;
      box-shadow: var(--shadow);
    }
    .favorite:hover:not(:disabled) {
      border-color: var(--color-accent);
    }
    .favorite:disabled {
      opacity: 0.7;
      cursor: progress;
    }
    .favorite:focus-visible {
      outline: 3px solid var(--color-focus);
      outline-offset: 2px;
    }
  `,
})
export class FavoriteButton {
  readonly packageId = input.required<string>();
  readonly packageTitle = input.required<string>();
  /** Só o ícone (cartões). O nome acessível vem do aria-label. */
  readonly compact = input(false);

  private readonly store = inject(FavoritesStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly busy = signal(false);
  protected readonly active = computed(
    () => this.auth.isAuthenticated() && this.store.has(this.packageId()),
  );
  protected readonly label = computed(() =>
    this.active()
      ? `Tirar ${this.packageTitle()} dos favoritos`
      : `Favoritar ${this.packageTitle()}`,
  );

  protected toggle(): void {
    if (!this.auth.isAuthenticated()) {
      this.auth.login(this.router.url);
      return;
    }
    this.busy.set(true);
    this.store.toggle(this.packageId()).subscribe({
      next: () => this.busy.set(false),
      error: (error: unknown) => {
        this.busy.set(false);
        if (!(error instanceof ApiError && error.kind === 'unauthorized')) {
          this.notifications.error(errorMessage(error));
        }
      },
    });
  }
}
