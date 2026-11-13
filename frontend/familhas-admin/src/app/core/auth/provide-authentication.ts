import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import {
  LogLevel,
  provideAuth,
  StsConfigLoader,
  StsConfigStaticLoader,
} from 'angular-auth-oidc-client';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { AuthService } from './auth.service';
import { OidcAuthService } from './oidc-auth.service';

/** Autenticação no Keycloak: client público familhas-admin, Authorization Code + PKCE (S256). */
export function provideAuthentication(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideAuth({
      loader: {
        provide: StsConfigLoader,
        useFactory: (config: AppConfig) => new StsConfigStaticLoader(oidcConfig(config)),
        deps: [APP_CONFIG],
      },
    }),
    { provide: AuthService, useClass: OidcAuthService },
    provideAppInitializer(() => inject(AuthService).init()),
  ]);
}

export function oidcConfig(config: AppConfig, origin = window.location.origin) {
  return {
    authority: config.oidc.authority,
    clientId: config.oidc.clientId,
    scope: config.oidc.scope,
    responseType: 'code', // Authorization Code; a biblioteca sempre usa PKCE S256 com 'code'
    redirectUrl: `${origin}/`,
    postLogoutRedirectUri: `${origin}/`,
    // Renovação por refresh token, sem iframe (cookies de terceiros bloqueados nos navegadores).
    silentRenew: true,
    useRefreshToken: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,
    ignoreNonceAfterRefresh: true,
    triggerAuthorizationResultEvent: true,
    // O token vai só pelo nosso jwtInterceptor, e só para /api/admin.
    secureRoutes: [],
    logLevel: LogLevel.Warn,
  };
}
