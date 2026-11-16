/**
 * Módulos do JSON que ainda não têm tela, com a etapa prevista (docs/requirements-matrix.md). A tela de módulo
 * pendente mostra isso como está: nada de botão que finge funcionar.
 */
export interface PendingModule {
  readonly title: string;
  readonly summary: string;
  readonly stage: number;
  /** Preenchido quando depende de algo externo ao código. */
  readonly blockedBy?: string;
}

export const PENDING_MODULES = {
  backup: {
    title: 'Backup e restauração',
    summary:
      'Exporta e restaura todas as tabelas de negócio num único arquivo, sem sobrescrever edições posteriores.',
    stage: 5,
  },
  voosClientes: {
    title: 'Voos Clientes',
    summary:
      'Voos dos clientes por data de ida, busca rápida por passageiro sem acento, arquivados e anexos.',
    stage: 3,
  },
  pesquisarVoos: {
    title: 'Pesquisar Voos',
    summary:
      'Cotação no Google Flights (SerpApi) por grupos de até 7 destinos, datas exatas ou flexíveis.',
    stage: 3,
  },
  vouchers: {
    title: 'Emitir Voucher',
    summary:
      'Vouchers de voo, hotel, transfer e passeio em A4, com histórico e envio por WhatsApp.',
    stage: 3,
  },
  postsCotacao: {
    title: 'Criar Posts de cotação',
    summary: 'Posts de pacote a partir do PDF de orçamento, em 5 layouts com a logo oficial.',
    stage: 4,
  },
  postsCruzeiros: {
    title: 'Posts de Cruzeiros',
    summary: 'Posts de cruzeiro nos layouts "Card no oceano" e "Oportunidade", Feed e Story.',
    stage: 4,
  },
  salvarImagens: {
    title: 'Salvar Imagens',
    summary: 'Ferramenta do app Story Image Saver para salvar e organizar imagens de stories.',
    stage: 4,
    blockedBy: 'código ou especificação do app original Story Image Saver',
  },
  postsInsta: {
    title: 'Criar posts Insta',
    summary: 'Fila de posts (Story, Feed, Reel e carrossel) com publicação pela Meta.',
    stage: 4,
  },
  roteiros: {
    title: 'Gerar Roteiro',
    summary: 'Proposta comercial de roteiro em uma página A4 com a identidade da agência.',
    stage: 4,
  },
  equipe: {
    title: 'Equipe',
    summary: 'Membros e convites da equipe pelo Keycloak (somente administrador).',
    stage: 5,
  },
  importarDados: {
    title: 'Importar dados',
    summary: 'Importação dos dados legados com IDs de origem únicos e pulo de duplicados.',
    stage: 5,
    blockedBy: 'amostras do formato dos dados legados',
  },
  documentos: {
    title: 'Documentos',
    summary:
      'Repositório privado de documentos e imagens com busca por nome e visualização de PDF.',
    stage: 3,
  },
} satisfies Record<string, PendingModule>;
