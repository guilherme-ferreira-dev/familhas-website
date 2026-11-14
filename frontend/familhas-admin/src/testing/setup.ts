import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../app/core/auth/auth.service';
import { APP_CONFIG, AppConfig } from '../app/core/config/app-config';
import { errorInterceptor } from '../app/core/errors/error.interceptor';
import { provideLocale } from '../app/core/locale';

export const API = 'http://api/api/admin';

/** Monta uma tela com HttpClient de teste e devolve fixture + backend falso. */
export async function render<T>(
  component: Type<T>,
  inputs: Record<string, unknown> = {},
): Promise<{ fixture: ComponentFixture<T>; http: HttpTestingController }> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      provideLocale(),
      { provide: APP_CONFIG, useValue: { apiBaseUrl: 'http://api' } as AppConfig },
      {
        provide: AuthService,
        useValue: { getAccessToken: () => Promise.resolve('tok'), login: () => undefined },
      },
    ],
  });
  const fixture = TestBed.createComponent(component);
  for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
  fixture.detectChanges();
  TestBed.tick();
  return { fixture, http: TestBed.inject(HttpTestingController) };
}

export function page<T>(content: T[], totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

/** Depois de responder as requisições: deixa os resources e o template atualizarem. */
export async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await Promise.resolve();
  TestBed.tick();
  fixture.detectChanges();
}
