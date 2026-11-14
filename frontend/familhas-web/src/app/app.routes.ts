import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

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
    data: { description: 'Busque pacotes, voos, hotéis e carros para viajar em família.' },
    loadComponent: () => import('./pages/search/search.page').then((m) => m.SearchPage),
  },
  {
    path: 'pacotes/:id',
    title: 'Pacote | Familhas',
    loadComponent: () =>
      import('./pages/package-detail/package-detail.page').then((m) => m.PackageDetailPage),
  },
  {
    path: 'cotacoes/:id',
    title: 'Sua cotação | Familhas',
    data: { noindex: true },
    canActivate: [authGuard],
    loadComponent: () => import('./pages/quote/quote.page').then((m) => m.QuotePage),
  },
  {
    path: 'checkout/:id',
    title: 'Pagamento | Familhas',
    data: { noindex: true },
    canActivate: [authGuard],
    loadComponent: () => import('./pages/checkout/checkout.page').then((m) => m.CheckoutPage),
  },
  {
    path: 'checkout/:id/sucesso',
    title: 'Confirmando pagamento | Familhas',
    canActivate: [authGuard],
    data: { outcome: 'sucesso', noindex: true },
    loadComponent: () =>
      import('./pages/checkout/checkout-return.page').then((m) => m.CheckoutReturnPage),
  },
  {
    path: 'checkout/:id/cancelado',
    title: 'Pagamento não concluído | Familhas',
    canActivate: [authGuard],
    data: { outcome: 'cancelado', noindex: true },
    loadComponent: () =>
      import('./pages/checkout/checkout-return.page').then((m) => m.CheckoutReturnPage),
  },
  {
    path: 'minhas-viagens',
    title: 'Minhas viagens | Familhas',
    data: { noindex: true },
    canActivate: [authGuard],
    loadComponent: () => import('./pages/my-trips/my-trips.page').then((m) => m.MyTripsPage),
  },
  {
    path: '**',
    title: 'Página não encontrada | Familhas',
    data: { noindex: true },
    loadComponent: () => import('./pages/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
