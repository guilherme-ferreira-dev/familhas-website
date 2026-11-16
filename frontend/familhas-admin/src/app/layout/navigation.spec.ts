import { NAVIGATION, visibleNavigation } from './navigation';

describe('menu do CRM', () => {
  it('tem os quatro grupos do JSON, sem módulos financeiros nem telas da Loja', () => {
    expect(NAVIGATION.map((g) => g.label)).toEqual([
      'Geral',
      'Viagens',
      'Marketing & Conteúdo',
      'Administração',
    ]);
    const labels = NAVIGATION.flatMap((g) => g.items.map((i) => i.label)).join(' ');
    for (const forbidden of [
      'Cartões',
      'Produtos',
      'Finanças',
      'Pedidos',
      'Pagamentos',
      'Reservas',
      'E-mails',
    ]) {
      expect(labels).not.toContain(forbidden);
    }
  });

  it('mostra só o que a pessoa pode ler e some com grupo vazio', () => {
    const marketing = new Set([
      'crm:dashboard:ler',
      'crm:posts-cotacao:ler',
      'crm:posts-cruzeiros:ler',
      'crm:imagens:ler',
      'crm:posts-insta:ler',
      'crm:roteiros:ler',
      'crm:documentos:ler',
    ]);
    const visible = visibleNavigation((p) => marketing.has(p));
    expect(visible.map((g) => g.label)).toEqual(['Geral', 'Marketing & Conteúdo', 'Administração']);
    expect(visible.flatMap((g) => g.items.map((i) => i.path))).not.toContain('/vouchers');
    expect(visible.find((g) => g.label === 'Administração')?.items.map((i) => i.label)).toEqual([
      'Documentos',
    ]);
  });

  it('sem permissões, menu vazio', () => {
    expect(visibleNavigation(() => false)).toEqual([]);
  });
});
