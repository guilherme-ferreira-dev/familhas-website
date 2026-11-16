import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CrmSession } from '../crm/crm-session';
import { AuthService } from './auth.service';

/**
 * Todas as telas do CRM. Sem sessão: vai para o Keycloak e volta para a rota pedida.
 * Com sessão mas sem papel do CRM: tela "sem permissão". A API também responde 403.
 * Keycloak fora do ar no bootstrap: tela "login indisponível", com a rota pedida para tentar de novo.
 */
export const teamGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.unavailable()) {
    return inject(Router).createUrlTree(['/login-indisponivel'], {
      queryParams: { voltar: state.url },
    });
  }
  if (!auth.isAuthenticated()) {
    auth.login(state.url);
    return false;
  }
  return auth.isTeamMember() ? true : inject(Router).parseUrl('/sem-permissao');
};

/** Telas da Loja (clientes, reservas, pagamentos, catálogo): ficam fora do menu e só o crm-admin abre. */
export const crmAdminGuard: CanActivateFn = () =>
  inject(AuthService).isCrmAdmin() ? true : inject(Router).parseUrl('/sem-permissao');

/**
 * Rota que exige uma permissão `crm:<recurso>:<ação>` em `data.permission`. Espera o perfil (`/api/crm/me`)
 * carregar. Só UX: a API confere de novo em cada chamada.
 */
export const permissionGuard: CanActivateFn = async (route) => {
  const permission = route.data['permission'] as string | undefined;
  if (!permission) return true;
  // inject() só vale antes do primeiro await.
  const session = inject(CrmSession);
  const router = inject(Router);
  await session.ready();
  return session.can(permission) ? true : router.parseUrl('/sem-permissao');
};
