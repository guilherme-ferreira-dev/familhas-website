/**
 * Menu do CRM, nos grupos do JSON funcional. Cada item exige a permissão de leitura do módulo (ADR 0013); a lista
 * visível sai de {@link visibleNavigation}. As telas da Loja (clientes, reservas, pagamentos, cotações, catálogo)
 * não entram aqui: só o crm-admin abre, pelo endereço.
 */
export interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly permission: string;
  readonly exact?: boolean;
}

export interface NavGroup {
  readonly label: string;
  readonly items: readonly NavItem[];
}

export const NAVIGATION: readonly NavGroup[] = [
  {
    label: 'Geral',
    items: [
      { path: '/', label: 'Visão geral', permission: 'crm:dashboard:ler', exact: true },
      { path: '/backup', label: 'Backup e restauração', permission: 'crm:backup:ler' },
    ],
  },
  {
    label: 'Viagens',
    items: [
      { path: '/voos-clientes', label: 'Voos Clientes', permission: 'crm:voos-clientes:ler' },
      { path: '/pesquisar-voos', label: 'Pesquisar Voos', permission: 'crm:pesquisa-voos:ler' },
      { path: '/vouchers', label: 'Emitir Voucher', permission: 'crm:vouchers:ler' },
    ],
  },
  {
    label: 'Marketing & Conteúdo',
    items: [
      {
        path: '/posts-cotacao',
        label: 'Criar Posts de cotação',
        permission: 'crm:posts-cotacao:ler',
      },
      {
        path: '/posts-cruzeiros',
        label: 'Posts de Cruzeiros',
        permission: 'crm:posts-cruzeiros:ler',
      },
      { path: '/salvar-imagens', label: 'Salvar Imagens', permission: 'crm:imagens:ler' },
      { path: '/posts-insta', label: 'Criar posts Insta', permission: 'crm:posts-insta:ler' },
      { path: '/roteiros', label: 'Gerar Roteiro', permission: 'crm:roteiros:ler' },
    ],
  },
  {
    label: 'Administração',
    items: [
      // "Equipe (somente admin)": só quem pode convidar enxerga.
      { path: '/equipe', label: 'Equipe', permission: 'crm:equipe:criar' },
      { path: '/importar-dados', label: 'Importar dados', permission: 'crm:importacao:ler' },
      { path: '/documentos', label: 'Documentos', permission: 'crm:documentos:ler' },
      { path: '/auditoria', label: 'Auditoria', permission: 'crm:auditoria:ler' },
    ],
  },
];

/** Grupos e itens que a pessoa pode abrir; grupo sem item some. */
export function visibleNavigation(
  can: (permission: string) => boolean,
  navigation: readonly NavGroup[] = NAVIGATION,
): NavGroup[] {
  return navigation
    .map((group) => ({ ...group, items: group.items.filter((item) => can(item.permission)) }))
    .filter((group) => group.items.length > 0);
}
