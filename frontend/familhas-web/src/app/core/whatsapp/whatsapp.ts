import { CurrencyPipe } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

/** O que a tela atual está mostrando, para a mensagem do WhatsApp já chegar com contexto. */
export type WhatsappContext =
  | { readonly kind: 'package'; readonly id: string; readonly title: string }
  | {
      readonly kind: 'quote';
      readonly id: string;
      readonly total: number;
      readonly currency: string;
    }
  | { readonly kind: 'booking'; readonly id: string };

/**
 * Contexto da tela atual. As páginas chamam set(); a troca de rota limpa sozinha,
 * então nenhuma tela herda o contexto da anterior.
 */
@Injectable({ providedIn: 'root' })
export class WhatsappContextService {
  private readonly state = signal<WhatsappContext | null>(null);
  readonly context = this.state.asReadonly();

  constructor() {
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationStart))
      .subscribe(() => this.state.set(null));
  }

  set(context: WhatsappContext): void {
    this.state.set(context);
  }
}

/** Monta a URL wa.me. Sem número configurado, devolve null (o botão não aparece). */
export function buildWhatsappUrl(phone: string, message: string): string | null {
  if (!phone) return null;
  return `https://wa.me/${phone}?text=${encodeURIComponent(message)}`;
}

/**
 * Texto pré-preenchido. LGPD: só identificadores da Familhas (pacote, cotação, reserva), nunca nome,
 * e-mail ou documento do cliente.
 */
export function whatsappMessage(
  context: WhatsappContext | null,
  defaultMessage: string,
  siteUrl: string,
  currency: CurrencyPipe,
): string {
  switch (context?.kind) {
    case 'package':
      return `Olá! Tenho interesse no pacote "${context.title}". Pode me ajudar? ${siteUrl}/pacotes/${context.id}`;
    case 'quote': {
      const total = currency.transform(context.total, context.currency, 'symbol', '1.2-2', 'pt-BR');
      return `Olá! Quero ajuda com a minha cotação ${shortId(context.id)} (total ${total}).`;
    }
    case 'booking':
      return `Olá! Quero ajuda com a minha reserva ${shortId(context.id)}.`;
    default:
      return defaultMessage;
  }
}

/** 8 primeiros caracteres do UUID: suficiente para o Enéas achar no Admin, curto para a conversa. */
export function shortId(id: string): string {
  return id.slice(0, 8).toUpperCase();
}
