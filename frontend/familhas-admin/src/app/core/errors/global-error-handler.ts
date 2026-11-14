import { ErrorHandler, inject, Injectable } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notifications/notification.service';
import { ApiError, errorMessage } from './api-error';

/** Último recurso para erros não tratados pela tela. Nunca manda nada para fora do navegador. */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);

  handleError(error: unknown): void {
    console.error(error);
    if (
      error instanceof ApiError &&
      (error.kind === 'unauthorized' || error.kind === 'forbidden')
    ) {
      return; // o interceptor já levou para o login ou para "sem permissão"
    }
    if (!(error instanceof ApiError) && this.auth.unavailable()) {
      return; // erro da biblioteca de login com o Keycloak fora: a tela "login indisponível" já explica
    }
    this.notifications.error(errorMessage(error));
  }
}
