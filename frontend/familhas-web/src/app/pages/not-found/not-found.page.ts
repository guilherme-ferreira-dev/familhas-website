import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="container stack lost">
      <h1>Página não encontrada</h1>
      <p>O endereço pode estar errado ou a oferta pode ter saído do ar.</p>
      <p><a routerLink="/" class="button">Voltar para o início</a></p>
    </section>
  `,
  styles: `
    .lost {
      justify-items: start;
      padding-block: var(--space-5);
    }
    .lost h1 {
      font-size: clamp(2.5rem, 7vw, 4.5rem);
    }
  `,
})
export class NotFoundPage {}
