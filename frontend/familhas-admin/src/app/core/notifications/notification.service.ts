import { Injectable, signal } from '@angular/core';

export interface Notice {
  readonly id: number;
  readonly kind: 'error' | 'info';
  readonly text: string;
}

const AUTO_DISMISS_MS = 8000;

/** Avisos curtos (toast) anunciados por leitor de tela. Erros de uma tela específica ficam na própria tela. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  private readonly items = signal<readonly Notice[]>([]);
  readonly notices = this.items.asReadonly();

  error(text: string): void {
    this.push('error', text);
  }

  info(text: string): void {
    this.push('info', text);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((n) => n.id !== id));
  }

  private push(kind: Notice['kind'], text: string): void {
    // Não empilha a mesma mensagem (ex.: várias chamadas falhando juntas).
    if (this.items().some((n) => n.text === text)) return;
    const notice: Notice = { id: this.nextId++, kind, text };
    this.items.update((list) => [...list.slice(-2), notice]);
    setTimeout(() => this.dismiss(notice.id), AUTO_DISMISS_MS);
  }
}
