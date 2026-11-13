import { Routes } from '@angular/router';
import { adminGuard } from './core/auth/admin.guard';
import { Shell } from './layout/shell';
import { NoPermissionPage } from './pages/no-permission.page';
import { NotFoundPage } from './pages/not-found.page';

export const routes: Routes = [
  { path: 'sem-permissao', component: NoPermissionPage, title: 'Sem permissão' },
  {
    path: '',
    component: Shell,
    canActivate: [adminGuard],
    canActivateChild: [adminGuard],
    children: [{ path: '**', component: NotFoundPage, title: 'Página não encontrada' }],
  },
];
