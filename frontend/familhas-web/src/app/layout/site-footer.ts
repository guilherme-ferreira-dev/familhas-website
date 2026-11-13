import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Brand } from './brand';

@Component({
  selector: 'app-site-footer',
  imports: [Brand],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="footer">
      <div class="container">
        <app-brand class="lead" />
        <p>Viagens em família, com atendimento de gente de verdade.</p>
        <p class="note">
          Pagamentos processados pelo Stripe. Seus dados de cartão não passam pela Familhas.
        </p>
      </div>
    </footer>
  `,
  styles: `
    .footer {
      margin: var(--space-4) var(--space-2) var(--space-2);
      padding-block: var(--space-3) calc(var(--space-3) + 3rem);
      border-radius: 1.5rem;
      background: var(--color-night);
      color: #e8f0ff;
      font-size: 0.875rem;
    }
    .container {
      display: grid;
      gap: var(--space-1);
    }
    .lead {
      font-size: 1.5rem;
      margin-block-end: var(--space-1);
    }
    .note {
      color: #c5d3f0;
    }
    @media (min-width: 48rem) {
      .footer {
        margin-inline: var(--space-3);
        padding-block-end: var(--space-3);
      }
    }
  `,
})
export class SiteFooter {}
