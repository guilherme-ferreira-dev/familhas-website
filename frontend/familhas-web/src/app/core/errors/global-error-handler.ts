import { ErrorHandler, inject, Injectable } from '@angular/core';
import { NotificationService } from '../notifications/notification.service';
import { ApiError, errorMessage } from './api-error';

/**
 * Último recurso para erros não tratados pela tela. Não manda nada para fora do navegador
 * (observabilidade de frontend fica para a Fase 11) e nunca loga dados pessoais.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notifications = inject(NotificationService);

  handleError(error: unknown): void {
    console.error(error);
    if (error instanceof ApiError && error.kind === 'unauthorized') {
      return; // o interceptor já mandou para o login
    }
    this.notifications.error(errorMessage(error));
  }
}
