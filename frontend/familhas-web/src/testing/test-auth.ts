import { Provider, signal } from '@angular/core';
import { AuthService, AuthUser } from '../app/core/auth/auth.service';

/**
 * AuthService falso para testes de tela. Logado, o token começa com o prefixo que os handlers do MSW aceitam.
 * Devolve o provider e os spies, para o teste conferir login/logout.
 */
export function provideTestAuth(loggedIn = false) {
  const user = signal<AuthUser | null>(
    loggedIn ? { name: 'Cliente', email: 'cliente@familhas.local' } : null,
  );
  const auth = {
    isAuthenticated: () => user() !== null,
    user: user.asReadonly(),
    init: vi.fn(() => Promise.resolve()),
    login: vi.fn(),
    logout: vi.fn(() => Promise.resolve()),
    getAccessToken: () => Promise.resolve(user() ? 'mock-token' : null),
  };
  const provider: Provider = { provide: AuthService, useValue: auth };
  return { provider, auth };
}
