import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="page">
      <h1>Página não encontrada</h1>
      <p><a routerLink="/">Voltar para o dashboard</a></p>
    </section>
  `,
})
export class NotFoundPage {}
