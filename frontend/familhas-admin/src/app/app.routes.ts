import { Routes } from '@angular/router';
import { crmAdminGuard, permissionGuard, teamGuard } from './core/auth/crm.guards';
import { PENDING_MODULES, PendingModule } from './layout/modules';
import { Shell } from './layout/shell';
import { LoginUnavailablePage } from './pages/login-unavailable.page';
import { NoPermissionPage } from './pages/no-permission.page';
import { NotFoundPage } from './pages/not-found.page';

export const routes: Routes = [
  { path: 'sem-permissao', component: NoPermissionPage, title: 'Sem permissão' },
  { path: 'login-indisponivel', component: LoginUnavailablePage, title: 'Login indisponível' },
  {
    path: '',
    component: Shell,
    canActivate: [teamGuard],
    canActivateChild: [teamGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Visão geral',
        data: { permission: 'crm:dashboard:ler' },
        canActivate: [permissionGuard],
        loadComponent: () => import('./pages/overview/overview.page').then((m) => m.OverviewPage),
      },
      pending('backup', 'crm:backup:ler', PENDING_MODULES.backup),
      pending('voos-clientes', 'crm:voos-clientes:ler', PENDING_MODULES.voosClientes),
      pending('pesquisar-voos', 'crm:pesquisa-voos:ler', PENDING_MODULES.pesquisarVoos),
      pending('vouchers', 'crm:vouchers:ler', PENDING_MODULES.vouchers),
      pending('posts-cotacao', 'crm:posts-cotacao:ler', PENDING_MODULES.postsCotacao),
      pending('posts-cruzeiros', 'crm:posts-cruzeiros:ler', PENDING_MODULES.postsCruzeiros),
      pending('salvar-imagens', 'crm:imagens:ler', PENDING_MODULES.salvarImagens),
      pending('posts-insta', 'crm:posts-insta:ler', PENDING_MODULES.postsInsta),
      pending('roteiros', 'crm:roteiros:ler', PENDING_MODULES.roteiros),
      pending('equipe', 'crm:equipe:criar', PENDING_MODULES.equipe),
      pending('importar-dados', 'crm:importacao:ler', PENDING_MODULES.importarDados),
      pending('documentos', 'crm:documentos:ler', PENDING_MODULES.documentos),
      {
        path: 'auditoria',
        title: 'Auditoria',
        data: { permission: 'crm:auditoria:ler' },
        canActivate: [permissionGuard],
        loadComponent: () => import('./pages/audit/audit.page').then((m) => m.AuditPage),
      },
      // Telas da Loja: fora do menu do CRM, preservadas para o crm-admin (ADR 0013).
      {
        path: 'loja',
        title: 'Loja: dashboard',
        canActivate: [crmAdminGuard],
        loadComponent: () =>
          import('./pages/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'clientes',
        canActivate: [crmAdminGuard],
        title: 'Clientes',
        loadComponent: () => import('./pages/clients/clients.page').then((m) => m.ClientsPage),
      },
      {
        path: 'clientes/:id',
        canActivate: [crmAdminGuard],
        title: 'Cliente',
        loadComponent: () =>
          import('./pages/clients/client-detail.page').then((m) => m.ClientDetailPage),
      },
      {
        path: 'reservas',
        canActivate: [crmAdminGuard],
        title: 'Reservas',
        loadComponent: () => import('./pages/bookings/bookings.page').then((m) => m.BookingsPage),
      },
      {
        path: 'reservas/:id',
        canActivate: [crmAdminGuard],
        title: 'Reserva',
        loadComponent: () =>
          import('./pages/bookings/booking-detail.page').then((m) => m.BookingDetailPage),
      },
      {
        path: 'pagamentos',
        canActivate: [crmAdminGuard],
        title: 'Pagamentos',
        loadComponent: () => import('./pages/payments/payments.page').then((m) => m.PaymentsPage),
      },
      {
        path: 'cotacoes',
        canActivate: [crmAdminGuard],
        title: 'Cotações',
        loadComponent: () => import('./pages/quotes/quotes.page').then((m) => m.QuotesPage),
      },
      {
        path: 'cotacoes/nova',
        canActivate: [crmAdminGuard],
        title: 'Nova cotação manual',
        loadComponent: () => import('./pages/quotes/new-quote.page').then((m) => m.NewQuotePage),
      },
      {
        path: 'cotacoes/:id',
        canActivate: [crmAdminGuard],
        title: 'Cotação',
        loadComponent: () =>
          import('./pages/quotes/quote-detail.page').then((m) => m.QuoteDetailPage),
      },
      {
        path: 'catalogo',
        canActivate: [crmAdminGuard],
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

/** Rota de módulo ainda sem tela: exige a permissão de leitura e mostra a etapa prevista. */
function pending(path: string, permission: string, module: PendingModule): Routes[number] {
  return {
    path,
    title: module.title,
    data: { permission, module },
    canActivate: [permissionGuard],
    loadComponent: () => import('./pages/pending-module.page').then((m) => m.PendingModulePage),
  };
}
