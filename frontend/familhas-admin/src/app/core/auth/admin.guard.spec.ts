import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';

describe('adminGuard', () => {
  const login = vi.fn();
  const authenticated = signal(false);
  const admin = signal(false);

  beforeEach(() => {
    login.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { isAuthenticated: authenticated, isAdmin: admin, login },
        },
      ],
    });
  });

  const run = (url: string) =>
    TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  it('sem sessão vai para o login guardando a rota', () => {
    authenticated.set(false);
    expect(run('/pagamentos')).toBe(false);
    expect(login).toHaveBeenCalledWith('/pagamentos');
  });

  it('cliente comum (sem ROLE_ADMIN) vai para "sem permissão"', () => {
    authenticated.set(true);
    admin.set(false);
    const result = run('/pagamentos');
    expect(result).toBeInstanceOf(UrlTree);
    expect(String(result)).toBe('/sem-permissao');
    expect(login).not.toHaveBeenCalled();
  });

  it('admin entra', () => {
    authenticated.set(true);
    admin.set(true);
    expect(run('/pagamentos')).toBe(true);
  });
});
