import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { isAdminApiRequest } from '../auth/jwt.interceptor';
import { APP_CONFIG } from '../config/app-config';
import { ApiError } from './api-error';

/**
 * Converte erros da API em ApiError. 401 = sessão ausente ou expirada: login e volta para a mesma tela.
 * 403 = token sem ROLE_ADMIN (papel removido no Keycloak, por exemplo): tela "sem permissão".
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || !isAdminApiRequest(req.url, apiBaseUrl)) {
        return throwError(() => error);
      }
      const apiError = ApiError.from(error);
      if (apiError.kind === 'unauthorized') {
        auth.login(router.url);
      } else if (apiError.kind === 'forbidden') {
        void router.navigateByUrl('/sem-permissao');
      }
      return throwError(() => apiError);
    }),
  );
};
