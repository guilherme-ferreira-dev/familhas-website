import { ErrorHandler, inject, Injectable } from '@angular/core';
import { NotificationService } from '../notifications/notification.service';
import { ApiError, errorMessage } from './api-error';

/** Último recurso para erros não tratados pela tela. Nunca manda nada para fora do navegador. */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notifications = inject(NotificationService);

  handleError(error: unknown): void {
    console.error(error);
    if (
      error instanceof ApiError &&
      (error.kind === 'unauthorized' || error.kind === 'forbidden')
    ) {
      return; // o interceptor já levou para o login ou para "sem permissão"
    }
    this.notifications.error(errorMessage(error));
  }
}
