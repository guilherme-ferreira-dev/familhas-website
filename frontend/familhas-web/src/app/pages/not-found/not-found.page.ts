import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="container stack">
      <h1>Página não encontrada</h1>
      <p>O endereço pode estar errado ou a oferta pode ter saído do ar.</p>
      <p><a routerLink="/" class="button">Voltar para o início</a></p>
    </section>
  `,
})
export class NotFoundPage {}
