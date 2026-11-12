import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-site-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="footer">
      <div class="container">
        <p>Familhas: viagens em família, com atendimento de gente de verdade.</p>
        <p class="muted">
          Pagamentos processados pelo Stripe. Seus dados de cartão não passam pela Familhas.
        </p>
      </div>
    </footer>
  `,
  styles: `
    .footer {
      margin-block-start: var(--space-5);
      padding-block: var(--space-4) calc(var(--space-5) + 3.5rem);
      border-block-start: 1px solid var(--color-border);
      font-size: 0.875rem;
    }
    .container {
      display: grid;
      gap: var(--space-2);
    }
  `,
})
export class SiteFooter {}
