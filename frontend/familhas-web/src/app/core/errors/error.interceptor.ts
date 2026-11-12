import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { APP_CONFIG } from '../config/app-config';
import { isApiRequest } from '../auth/jwt.interceptor';
import { ApiError } from './api-error';

/**
 * Converte erros da familhas-api em ApiError. 401 da API = sessão ausente ou expirada: manda para o login
 * e volta para a mesma tela. As telas tratam o resto com errorMessage().
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || !isApiRequest(req.url, apiBaseUrl)) {
        return throwError(() => error);
      }
      const apiError = ApiError.from(error);
      if (apiError.kind === 'unauthorized') {
        auth.login(router.url);
      }
      return throwError(() => apiError);
    }),
  );
};
