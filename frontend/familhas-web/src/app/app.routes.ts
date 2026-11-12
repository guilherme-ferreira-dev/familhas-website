import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '**',
    title: 'Página não encontrada | Familhas',
    loadComponent: () => import('./pages/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
