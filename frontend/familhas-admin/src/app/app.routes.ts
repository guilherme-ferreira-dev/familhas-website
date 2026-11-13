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
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Dashboard',
        loadComponent: () =>
          import('./pages/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'clientes',
        title: 'Clientes',
        loadComponent: () => import('./pages/clients/clients.page').then((m) => m.ClientsPage),
      },
      {
        path: 'clientes/:id',
        title: 'Cliente',
        loadComponent: () =>
          import('./pages/clients/client-detail.page').then((m) => m.ClientDetailPage),
      },
      {
        path: 'reservas',
        title: 'Reservas',
        loadComponent: () => import('./pages/bookings/bookings.page').then((m) => m.BookingsPage),
      },
      {
        path: 'reservas/:id',
        title: 'Reserva',
        loadComponent: () =>
          import('./pages/bookings/booking-detail.page').then((m) => m.BookingDetailPage),
      },
      {
        path: 'pagamentos',
        title: 'Pagamentos',
        loadComponent: () => import('./pages/payments/payments.page').then((m) => m.PaymentsPage),
      },
      {
        path: 'cotacoes',
        title: 'Cotações',
        loadComponent: () => import('./pages/quotes/quotes.page').then((m) => m.QuotesPage),
      },
      {
        path: 'cotacoes/nova',
        title: 'Nova cotação manual',
        loadComponent: () => import('./pages/quotes/new-quote.page').then((m) => m.NewQuotePage),
      },
      {
        path: 'cotacoes/:id',
        title: 'Cotação',
        loadComponent: () =>
          import('./pages/quotes/quote-detail.page').then((m) => m.QuoteDetailPage),
      },
      {
        path: 'catalogo',
        title: 'Catálogo',
        loadComponent: () => import('./pages/catalog/catalog-shell').then((m) => m.CatalogShell),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'voos' },
          {
            path: 'promocoes',
            title: 'Promoções',
            loadComponent: () =>
              import('./pages/catalog/promotions.page').then((m) => m.PromotionsPage),
          },
          {
            path: 'margem',
            title: 'Margem e taxa',
            loadComponent: () => import('./pages/catalog/pricing.page').then((m) => m.PricingPage),
          },
          {
            path: ':tipo',
            title: 'Catálogo',
            loadComponent: () =>
              import('./pages/catalog/catalog-list.page').then((m) => m.CatalogListPage),
          },
          {
            path: ':tipo/novo',
            title: 'Novo item do catálogo',
            loadComponent: () =>
              import('./pages/catalog/catalog-form.page').then((m) => m.CatalogFormPage),
          },
          {
            path: ':tipo/:id',
            title: 'Editar item do catálogo',
            loadComponent: () =>
              import('./pages/catalog/catalog-form.page').then((m) => m.CatalogFormPage),
          },
        ],
      },
      { path: '**', component: NotFoundPage, title: 'Página não encontrada' },
    ],
  },
];
