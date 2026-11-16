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
  // Roteiro A4 (uma página) fora do layout do CRM.
  {
    path: 'roteiros/:id/imprimir',
    title: 'Roteiro para impressão',
    canActivate: [teamGuard, permissionGuard],
    data: { permission: 'crm:roteiros:ler' },
    loadComponent: () =>
      import('./pages/itineraries/itinerary-print.page').then((m) => m.ItineraryPrintPage),
  },
  // Voucher A4 fora do layout do CRM (sem menu na impressão).
  {
    path: 'vouchers/:id/imprimir',
    title: 'Voucher para impressão',
    canActivate: [teamGuard, permissionGuard],
    data: { permission: 'crm:vouchers:ler' },
    loadComponent: () =>
      import('./pages/vouchers/voucher-print.page').then((m) => m.VoucherPrintPage),
  },
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
      crm('voos-clientes', 'Voos Clientes', 'crm:voos-clientes:ler', () =>
        import('./pages/client-flights/client-flights.page').then((m) => m.ClientFlightsPage),
      ),
      crm('voos-clientes/novo', 'Novo voo', 'crm:voos-clientes:criar', () =>
        import('./pages/client-flights/client-flight-form.page').then(
          (m) => m.ClientFlightFormPage,
        ),
      ),
      crm('voos-clientes/:id', 'Voo', 'crm:voos-clientes:ler', () =>
        import('./pages/client-flights/client-flight-detail.page').then(
          (m) => m.ClientFlightDetailPage,
        ),
      ),
      crm('voos-clientes/:id/editar', 'Editar voo', 'crm:voos-clientes:editar', () =>
        import('./pages/client-flights/client-flight-form.page').then(
          (m) => m.ClientFlightFormPage,
        ),
      ),
      crm('pesquisar-voos', 'Pesquisar Voos', 'crm:pesquisa-voos:ler', () =>
        import('./pages/flight-search/flight-search.page').then((m) => m.FlightSearchPage),
      ),
      crm('vouchers', 'Emitir Voucher', 'crm:vouchers:ler', () =>
        import('./pages/vouchers/vouchers.page').then((m) => m.VouchersPage),
      ),
      crm('vouchers/novo', 'Emitir voucher', 'crm:vouchers:criar', () =>
        import('./pages/vouchers/voucher-form.page').then((m) => m.VoucherFormPage),
      ),
      crm('vouchers/:id', 'Voucher', 'crm:vouchers:ler', () =>
        import('./pages/vouchers/voucher-detail.page').then((m) => m.VoucherDetailPage),
      ),
      crm('vouchers/:id/editar', 'Corrigir voucher', 'crm:vouchers:editar', () =>
        import('./pages/vouchers/voucher-form.page').then((m) => m.VoucherFormPage),
      ),
      crm('posts-cotacao', 'Criar Posts de cotação', 'crm:posts-cotacao:ler', () =>
        import('./pages/marketing/quote-post.page').then((m) => m.QuotePostPage),
      ),
      crm('posts-cruzeiros', 'Posts de Cruzeiros', 'crm:posts-cruzeiros:ler', () =>
        import('./pages/marketing/cruise-post.page').then((m) => m.CruisePostPage),
      ),
      pending('salvar-imagens', 'crm:imagens:ler', PENDING_MODULES.salvarImagens),
      crm('posts-insta', 'Criar posts Insta', 'crm:posts-insta:ler', () =>
        import('./pages/marketing/instagram-posts.page').then((m) => m.InstagramPostsPage),
      ),
      crm('posts-insta/novo', 'Novo post', 'crm:posts-insta:criar', () =>
        import('./pages/marketing/instagram-post.page').then((m) => m.InstagramPostPage),
      ),
      crm('posts-insta/:id', 'Post do Instagram', 'crm:posts-insta:ler', () =>
        import('./pages/marketing/instagram-post.page').then((m) => m.InstagramPostPage),
      ),
      crm('roteiros', 'Gerar Roteiro', 'crm:roteiros:ler', () =>
        import('./pages/itineraries/itineraries.page').then((m) => m.ItinerariesPage),
      ),
      crm('roteiros/novo', 'Novo roteiro', 'crm:roteiros:criar', () =>
        import('./pages/itineraries/itinerary.page').then((m) => m.ItineraryPage),
      ),
      crm('roteiros/:id', 'Roteiro', 'crm:roteiros:ler', () =>
        import('./pages/itineraries/itinerary.page').then((m) => m.ItineraryPage),
      ),
      pending('equipe', 'crm:equipe:criar', PENDING_MODULES.equipe),
      pending('importar-dados', 'crm:importacao:ler', PENDING_MODULES.importarDados),
      crm('documentos', 'Documentos', 'crm:documentos:ler', () =>
        import('./pages/documents/documents.page').then((m) => m.DocumentsPage),
      ),
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

/** Rota de módulo do CRM: exige a permissão em `data.permission`. */
function crm(
  path: string,
  title: string,
  permission: string,
  loadComponent: NonNullable<Routes[number]['loadComponent']>,
): Routes[number] {
  return { path, title, data: { permission }, canActivate: [permissionGuard], loadComponent };
}
