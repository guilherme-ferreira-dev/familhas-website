import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationService } from './notification.service';

@Component({
  selector: 'app-notification-region',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="region" aria-live="polite" aria-atomic="false">
      @for (notice of service.notices(); track notice.id) {
        <div class="notice" [class.notice--error]="notice.kind === 'error'" role="status">
          <p>{{ notice.text }}</p>
          <button type="button" (click)="service.dismiss(notice.id)" aria-label="Fechar aviso">
            ×
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .region {
      position: fixed;
      inset-inline-end: var(--space-3);
      inset-block-end: var(--space-3);
      z-index: 50;
      display: grid;
      gap: var(--space-2);
      width: min(28rem, calc(100vw - 2rem));
      pointer-events: none;
    }
    .notice {
      display: flex;
      gap: var(--space-2);
      align-items: center;
      padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
      box-shadow: var(--shadow);
      border-inline-start: 4px solid var(--color-primary);
      pointer-events: auto;
    }
    .notice--error {
      border-inline-start-color: var(--color-danger);
    }
    p {
      margin: 0;
      flex: 1;
    }
    button {
      min-width: 2.25rem;
      min-height: 2.25rem;
      border: 0;
      background: transparent;
      font-size: 1.25rem;
      cursor: pointer;
      color: inherit;
    }
  `,
})
export class NotificationRegion {
  protected readonly service = inject(NotificationService);
}
