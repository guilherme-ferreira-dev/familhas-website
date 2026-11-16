import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { CrmSession } from '../crm/crm-session';
import { AuthService } from './auth.service';
import { crmAdminGuard, permissionGuard, teamGuard } from './crm.guards';

describe('guards do CRM', () => {
  const login = vi.fn();
  const authenticated = signal(false);
  const team = signal(false);
  const crmAdmin = signal(false);
  const unavailable = signal(false);
  const permissions = new Set<string>();

  beforeEach(() => {
    login.mockReset();
    unavailable.set(false);
    permissions.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: authenticated,
            isTeamMember: team,
            isCrmAdmin: crmAdmin,
            unavailable,
            login,
          },
        },
        {
          provide: CrmSession,
          useValue: { ready: () => Promise.resolve(), can: (p: string) => permissions.has(p) },
        },
      ],
    });
  });

  const state = (url: string) => ({ url }) as RouterStateSnapshot;
  const route = (data: Record<string, unknown> = {}) =>
    ({ data }) as unknown as ActivatedRouteSnapshot;
  const runTeam = (url: string) =>
    TestBed.runInInjectionContext(() => teamGuard(route(), state(url)));

  describe('teamGuard', () => {
    it('sem sessão vai para o login guardando a rota', () => {
      authenticated.set(false);
      expect(runTeam('/vouchers')).toBe(false);
      expect(login).toHaveBeenCalledWith('/vouchers');
    });

    it('logado sem papel do CRM vai para "sem permissão"', () => {
      authenticated.set(true);
      team.set(false);
      const result = runTeam('/vouchers');
      expect(result).toBeInstanceOf(UrlTree);
      expect(String(result)).toBe('/sem-permissao');
      expect(login).not.toHaveBeenCalled();
    });

    it('membro da equipe entra', () => {
      authenticated.set(true);
      team.set(true);
      expect(runTeam('/vouchers')).toBe(true);
    });

    it('Keycloak fora do ar vai para "login indisponível" guardando a rota', () => {
      unavailable.set(true);
      const result = runTeam('/vouchers');
      expect(String(result)).toBe('/login-indisponivel?voltar=%2Fvouchers');
      expect(login).not.toHaveBeenCalled();
    });
  });

  describe('crmAdminGuard (telas da Loja)', () => {
    const run = () =>
      TestBed.runInInjectionContext(() => crmAdminGuard(route(), state('/pagamentos')));

    it('só o crm-admin abre', () => {
      crmAdmin.set(false);
      expect(String(run())).toBe('/sem-permissao');
      crmAdmin.set(true);
      expect(run()).toBe(true);
    });
  });

  describe('permissionGuard', () => {
    const run = (data: Record<string, unknown>) =>
      TestBed.runInInjectionContext(() => permissionGuard(route(data), state('/x')));

    it('exige a permissão da rota depois de carregar o perfil', async () => {
      expect(String(await run({ permission: 'crm:vouchers:ler' }))).toBe('/sem-permissao');
      permissions.add('crm:vouchers:ler');
      expect(await run({ permission: 'crm:vouchers:ler' })).toBe(true);
    });

    it('rota sem permissão declarada passa', async () => {
      expect(await run({})).toBe(true);
    });
  });
});
