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
  salvarImagens: {
    title: 'Salvar Imagens',
    summary: 'Ferramenta do app Story Image Saver para salvar e organizar imagens de stories.',
    stage: 4,
    blockedBy: 'código ou especificação do app original Story Image Saver',
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
} satisfies Record<string, PendingModule>;
