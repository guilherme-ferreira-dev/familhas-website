import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { Tone } from './labels';

/** Etiqueta de status: cor + texto (a cor nunca é a única informação). */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [attr.data-tone]="tone()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.125rem 0.5rem;
      border-radius: 999px;
      font-size: 0.8125rem;
      font-weight: 600;
      white-space: nowrap;
      background: var(--color-neutral-bg);
      color: var(--color-text);
    }
    [data-tone='success'] {
      background: var(--color-success-bg);
      color: var(--color-success);
    }
    [data-tone='warning'] {
      background: var(--color-warning-bg);
      color: var(--color-warning);
    }
    [data-tone='danger'] {
      background: var(--color-danger-bg);
      color: var(--color-danger);
    }
    [data-tone='info'] {
      background: #e6eeff;
      color: var(--color-primary-hover);
    }
  `,
})
export class StatusBadge {
  readonly label = input.required<string>();
  readonly tone = input<Tone>('neutral');
}
