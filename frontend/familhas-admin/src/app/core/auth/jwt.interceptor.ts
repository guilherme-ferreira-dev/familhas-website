import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';

/**
 * Anexa o access token SOMENTE às chamadas para `{apiBaseUrl}/api/admin/...`, o único prefixo que o Admin
 * consome. Qualquer outra URL (Keycloak, config.json, outras rotas da API) nunca recebe o token.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);
  if (!isAdminApiRequest(req.url, apiBaseUrl) || req.headers.has('Authorization')) {
    return next(req);
  }
  const auth = inject(AuthService);
  return from(auth.getAccessToken()).pipe(
    switchMap((token) =>
      next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req),
    ),
  );
};

export function isAdminApiRequest(url: string, apiBaseUrl: string): boolean {
  return url.startsWith(`${apiBaseUrl}/api/admin/`);
}
