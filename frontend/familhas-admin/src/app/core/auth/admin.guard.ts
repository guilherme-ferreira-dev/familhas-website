import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Todas as telas do Admin. Sem sessão: vai para o Keycloak e volta para a rota pedida.
 * Com sessão mas sem ROLE_ADMIN (cliente da Loja): tela "sem permissão". A API também responde 403.
 * Keycloak fora do ar no bootstrap: tela "login indisponível", com a rota pedida para tentar de novo.
 */
export const adminGuard: CanActivateFn = (_route, state) => {
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
  return auth.isAdmin() ? true : inject(Router).parseUrl('/sem-permissao');
};
