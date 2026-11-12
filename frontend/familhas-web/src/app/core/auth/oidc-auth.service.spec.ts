import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of, throwError } from 'rxjs';
import { AppConfig } from '../config/app-config';
import { OidcAuthService } from './oidc-auth.service';
import { oidcConfig } from './provide-authentication';

describe('OidcAuthService', () => {
  const authenticated = signal({ isAuthenticated: false, allConfigsAuthenticated: [] });
  const userData = signal<{ userData: unknown; allUserData: [] }>({
    userData: null,
    allUserData: [],
  });
  const oidc = {
    authenticated,
    userData,
    checkAuth: vi.fn(),
    authorize: vi.fn(),
    logoff: vi.fn(),
    logoffLocal: vi.fn(),
    getAccessToken: vi.fn(),
  };
  const router = { url: '/pacotes/1', navigateByUrl: vi.fn().mockResolvedValue(true) };
  let service: OidcAuthService;

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    authenticated.set({ isAuthenticated: false, allConfigsAuthenticated: [] });
    TestBed.configureTestingModule({
      providers: [
        OidcAuthService,
        { provide: OidcSecurityService, useValue: oidc },
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(OidcAuthService);
  });

  it('login guarda a rota atual e chama o authorize (PKCE)', () => {
    service.login();
    expect(sessionStorage.getItem('familhas.returnUrl')).toBe('/pacotes/1');
    expect(oidc.authorize).toHaveBeenCalled();
  });

  it('depois do callback volta para a rota guardada', async () => {
    sessionStorage.setItem('familhas.returnUrl', '/checkout/9');
    oidc.checkAuth.mockReturnValue(of({ isAuthenticated: true }));
    await service.init();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/checkout/9', { replaceUrl: true });
  });

  it('Keycloak fora do ar não derruba a Loja', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    oidc.checkAuth.mockReturnValue(throwError(() => new Error('offline')));
    await expect(service.init()).resolves.toBeUndefined();
  });

  it('expõe o primeiro nome do usuário logado', () => {
    authenticated.set({ isAuthenticated: true, allConfigsAuthenticated: [] });
    userData.set({ userData: { given_name: 'Ana', email: 'ana@x.com' }, allUserData: [] });
    expect(service.user()).toEqual({ name: 'Ana', email: 'ana@x.com' });
  });

  it('sem sessão não pede token', async () => {
    expect(await service.getAccessToken()).toBeNull();
    expect(oidc.getAccessToken).not.toHaveBeenCalled();
  });

  it('se o logoff no Keycloak falhar, encerra a sessão local', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    oidc.logoff.mockReturnValue(throwError(() => new Error('offline')));
    await service.logout();
    expect(oidc.logoffLocal).toHaveBeenCalled();
  });
});

describe('oidcConfig', () => {
  it('usa Authorization Code, refresh token e redirect na própria origem', () => {
    const config = oidcConfig(
      {
        oidc: {
          authority: 'https://kc/realms/familhas',
          clientId: 'familhas-web',
          scope: 'openid',
        },
      } as AppConfig,
      'http://localhost:4200',
    );
    expect(config).toMatchObject({
      responseType: 'code',
      clientId: 'familhas-web',
      redirectUrl: 'http://localhost:4200/',
      postLogoutRedirectUri: 'http://localhost:4200/',
      useRefreshToken: true,
      secureRoutes: [],
    });
  });
});
