import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Familhas | Viagens em família',
    loadComponent: () => import('./pages/home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'busca',
    title: 'Buscar viagens | Familhas',
    loadComponent: () => import('./pages/search/search.page').then((m) => m.SearchPage),
  },
  {
    path: '**',
    title: 'Página não encontrada | Familhas',
    loadComponent: () => import('./pages/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
