import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  const login = vi.fn();
  const authenticated = signal(false);

  beforeEach(() => {
    login.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAuthenticated: authenticated, login } }],
    });
  });

  const run = (url: string) =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  it('libera quem está logado', () => {
    authenticated.set(true);
    expect(run('/checkout/1')).toBe(true);
    expect(login).not.toHaveBeenCalled();
  });

  it('manda para o login guardando a rota pedida', () => {
    authenticated.set(false);
    expect(run('/checkout/1')).toBe(false);
    expect(login).toHaveBeenCalledWith('/checkout/1');
  });
});
