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
      inset-inline: var(--space-3);
      inset-block-start: var(--space-3);
      z-index: 50;
      display: grid;
      gap: var(--space-2);
      max-width: 32rem;
      margin-inline: auto;
      pointer-events: none;
    }
    .notice {
      display: flex;
      gap: var(--space-2);
      align-items: start;
      padding: var(--space-3);
      border-radius: var(--radius);
      background: var(--color-surface);
      color: var(--color-text);
      box-shadow: var(--shadow);
      border: 1px solid var(--color-primary);
      pointer-events: auto;
    }
    .notice--error {
      border-color: var(--color-danger);
    }
    p {
      margin: 0;
      flex: 1;
    }
    button {
      min-width: 2.75rem;
      min-height: 2.75rem;
      border: 0;
      background: transparent;
      font-size: 1.5rem;
      cursor: pointer;
      color: inherit;
    }
  `,
})
export class NotificationRegion {
  protected readonly service = inject(NotificationService);
}
