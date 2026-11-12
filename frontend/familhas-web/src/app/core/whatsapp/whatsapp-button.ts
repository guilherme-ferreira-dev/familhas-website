import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { APP_CONFIG } from '../config/app-config';
import { buildWhatsappUrl, whatsappMessage, WhatsappContextService } from './whatsapp';

/** Botão flutuante presente em todas as telas (fica no shell). Número vem do config.json do ambiente. */
@Component({
  selector: 'app-whatsapp-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CurrencyPipe],
  template: `
    @if (href(); as url) {
      <a
        class="whatsapp"
        [href]="url"
        target="_blank"
        rel="noopener noreferrer"
        aria-label="Falar com a Familhas no WhatsApp (abre em nova aba)"
      >
        <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true" focusable="false">
          <path
            fill="currentColor"
            d="M16 3a13 13 0 0 0-11.2 19.6L3 29l6.6-1.7A13 13 0 1 0 16 3Zm0 23.7c-2 0-4-.5-5.7-1.6l-.4-.2-3.9 1 1-3.8-.3-.4A10.7 10.7 0 1 1 16 26.7Zm5.9-8c-.3-.2-1.9-1-2.2-1-.3-.1-.5-.2-.7.2l-1 1.2c-.2.2-.4.3-.7.1a8.8 8.8 0 0 1-4.4-3.8c-.3-.6.3-.5.9-1.7.1-.2 0-.4 0-.6l-1-2.4c-.3-.6-.5-.5-.7-.5h-.6c-.2 0-.6.1-.9.4-.3.3-1.2 1.2-1.2 2.9s1.2 3.3 1.4 3.6c.2.2 2.4 3.7 5.8 5.1 2.2.9 3 1 4.1.8.7-.1 1.9-.8 2.2-1.5.3-.8.3-1.4.2-1.5l-.5-.3Z"
          />
        </svg>
        <span class="label">Fale conosco</span>
      </a>
    }
  `,
  styles: `
    .whatsapp {
      position: fixed;
      inset-inline-end: max(var(--space-3), env(safe-area-inset-right));
      inset-block-end: max(var(--space-3), env(safe-area-inset-bottom));
      z-index: 40;
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      min-width: 3.5rem;
      min-height: 3.5rem;
      padding: var(--space-3);
      border-radius: 999px;
      background: var(--color-whatsapp);
      color: #fff;
      font-weight: 600;
      text-decoration: none;
      box-shadow: var(--shadow);
    }
    .whatsapp:hover {
      filter: brightness(0.92);
    }
    .label {
      display: none;
    }
    @media (min-width: 48rem) {
      .label {
        display: inline;
      }
      .whatsapp {
        padding-inline: var(--space-4);
      }
    }
  `,
})
export class WhatsappButton {
  private readonly config = inject(APP_CONFIG);
  private readonly context = inject(WhatsappContextService).context;
  private readonly currency = inject(CurrencyPipe);

  protected readonly href = computed(() =>
    buildWhatsappUrl(
      this.config.whatsapp.phone,
      whatsappMessage(
        this.context(),
        this.config.whatsapp.defaultMessage,
        this.config.siteUrl,
        this.currency,
      ),
    ),
  );
}
