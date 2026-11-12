import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';

/**
 * Anexa o access token SOMENTE às chamadas para a familhas-api (`{apiBaseUrl}/api/...`).
 * Qualquer outra origem (Stripe, CDN, Keycloak) nunca recebe o token.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);
  if (!isApiRequest(req.url, apiBaseUrl) || req.headers.has('Authorization')) {
    return next(req);
  }
  const auth = inject(AuthService);
  return from(auth.getAccessToken()).pipe(
    switchMap((token) =>
      next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req),
    ),
  );
};

export function isApiRequest(url: string, apiBaseUrl: string): boolean {
  return url.startsWith(`${apiBaseUrl}/api/`);
}
