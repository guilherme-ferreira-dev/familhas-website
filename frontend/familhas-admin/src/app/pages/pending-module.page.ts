import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { PendingModule } from '../layout/modules';

/** Módulo do menu que ainda não foi construído: diz em que etapa chega, ou o que o bloqueia. */
@Component({
  selector: 'app-pending-module-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="titulo">
      <div class="page-header">
        <div>
          <h1 id="titulo">{{ module().title }}</h1>
          <p>{{ module().summary }}</p>
        </div>
      </div>
      @if (module().blockedBy; as blockedBy) {
        <p class="alert" role="status">
          <strong>Bloqueado.</strong> Este módulo aguarda {{ blockedBy }}.
        </p>
      } @else {
        <p class="alert alert--info" role="status">
          <strong>Em construção.</strong> Este módulo chega na Etapa {{ module().stage }} do CRM.
        </p>
      }
    </section>
  `,
})
export class PendingModulePage {
  /** Vem de `data.module` da rota (withComponentInputBinding). */
  readonly module = input.required<PendingModule>();
}
