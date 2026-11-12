import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/** Única origem para onde a Loja manda o cliente pagar (Stripe Checkout hospedado). */
export const STRIPE_CHECKOUT_ORIGIN = 'https://checkout.stripe.com';

/**
 * Passo de pagamento isolado. Hoje: redirect para o Stripe Checkout hospedado (ADR 0007 da Fase 6).
 * Se um dia virar Payment Element, só esta peça muda.
 */
@Injectable({ providedIn: 'root' })
export class PaymentRedirect {
  /** Origens extras aceitas: só a própria Loja, e só no build `mock` (não há Stripe lá). */
  protected readonly extraOrigins = environment.mocks ? [window.location.origin] : [];

  go(checkoutUrl: string): void {
    if (!isAllowedCheckoutUrl(checkoutUrl, this.extraOrigins)) {
      throw new Error('URL de pagamento recusada: origem não permitida.');
    }
    this.navigate(checkoutUrl);
  }

  protected navigate(url: string): void {
    window.location.assign(url);
  }
}

/** Defesa contra open redirect: a URL vem da API, mas só seguimos para o Stripe. */
export function isAllowedCheckoutUrl(url: string, extraOrigins: readonly string[] = []): boolean {
  try {
    const parsed = new URL(url);
    return parsed.origin === STRIPE_CHECKOUT_ORIGIN || extraOrigins.includes(parsed.origin);
  } catch {
    return false;
  }
}
