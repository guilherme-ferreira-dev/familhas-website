import {
  EnvironmentProviders,
  makeEnvironmentProviders,
  provideAppInitializer,
  inject,
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

/**
 * Autenticação real (Keycloak). No build `mock` este arquivo é trocado por
 * provide-authentication.mock.ts (fileReplacements), sem Keycloak.
 */
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
    // Renovação por refresh token (sem iframe: cookies de terceiros são bloqueados nos navegadores).
    silentRenew: true,
    useRefreshToken: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,
    ignoreNonceAfterRefresh: true,
    triggerAuthorizationResultEvent: true,
    // Não usamos o interceptor da biblioteca: o nosso (jwtInterceptor) só envia o token para a API.
    secureRoutes: [],
    logLevel: LogLevel.Warn,
  };
}
