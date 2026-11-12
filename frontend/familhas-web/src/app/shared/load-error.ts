import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { errorMessage } from '../core/errors/api-error';

/** Erro de carregamento dentro da tela, com "tentar de novo". Anunciado como alerta. */
@Component({
  selector: 'app-load-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="alert" role="alert">
      <p>{{ message() }}</p>
      <button type="button" class="button button--ghost" (click)="retry.emit()">
        Tentar de novo
      </button>
    </div>
  `,
  styles: `
    .alert {
      display: grid;
      gap: var(--space-2);
      justify-items: start;
    }
  `,
})
export class LoadError {
  readonly error = input.required<unknown>();
  readonly retry = output<void>();
  protected readonly message = computed(() => errorMessage(this.error()));
}
