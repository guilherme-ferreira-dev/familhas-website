import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  ErrorHandler,
  isDevMode,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { provideRouter, TitleStrategy, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/jwt.interceptor';
import { provideAuthentication } from './core/auth/provide-authentication';
import { errorInterceptor } from './core/errors/error.interceptor';
import { GlobalErrorHandler } from './core/errors/global-error-handler';
import { provideLocale } from './core/locale';
import { AdminTitleStrategy } from './core/title-strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([jwtInterceptor, errorInterceptor])),
    provideAuthentication(),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    provideLocale(),
    { provide: TitleStrategy, useClass: AdminTitleStrategy },
    // PWA instalável (ADR 0018): o service worker guarda só os arquivos do app (ngsw-config.json, sem dataGroups).
    // Respostas da API, documentos e o config.json nunca ficam em cache.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
