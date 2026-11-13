import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Marca provisória: "Familhas" em branco com "Voe" em amarelo acima, como descrito pela agência.
 * Trocar pelo arquivo oficial do logo quando estiver no repositório.
 */
@Component({
  selector: 'app-brand',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="voe" aria-hidden="true">Voe</span><span class="name">Familhas</span>`,
  styles: `
    :host {
      display: inline-grid;
      line-height: 1;
    }
    .voe {
      justify-self: start;
      font-family: var(--font-body);
      font-weight: 700;
      font-size: 0.75em;
      letter-spacing: 0.08em;
      color: var(--color-yellow);
    }
    .name {
      font-family: var(--font-display);
      font-weight: 700;
      letter-spacing: -0.03em;
      color: #fff;
    }
  `,
})
export class Brand {}
