import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { isOwnApiRequest } from '../auth/jwt.interceptor';
import { APP_CONFIG } from '../config/app-config';
import { ApiError } from './api-error';

/**
 * Converte erros da API em ApiError. 401 = sessão ausente ou expirada: login e volta para a mesma tela.
 * 403 sem nenhum papel do CRM (papel removido no Keycloak, por exemplo): tela "sem permissão". 403 de quem é da
 * equipe mas não tem a permissão daquela ação (ex.: atendente excluindo): só o erro, para a tela mostrar a mensagem.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || !isOwnApiRequest(req.url, apiBaseUrl)) {
        return throwError(() => error);
      }
      const apiError = ApiError.from(error);
      if (apiError.kind === 'unauthorized') {
        auth.login(router.url);
      } else if (apiError.kind === 'forbidden' && !auth.isTeamMember()) {
        void router.navigateByUrl('/sem-permissao');
      }
      return throwError(() => apiError);
    }),
  );
};
