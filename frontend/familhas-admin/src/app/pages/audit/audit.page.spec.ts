import { API, page, render, settle } from '../../../testing/setup';
import { AuditPage } from './audit.page';

describe('AuditPage', () => {
  it('filtra e mostra quem fez o quê, inclusive recusas', async () => {
    const { fixture, http } = await render(AuditPage, { recurso: 'payments', acao: 'EXPORT' });

    const req = http.expectOne((r) => r.url === `${API}/audit-logs`);
    expect(req.request.params.get('resource')).toBe('payments');
    expect(req.request.params.get('action')).toBe('EXPORT');
    req.flush(
      page([
        {
          id: 'a1',
          actorSubject: 'kc-1',
          actorUsername: 'admin.familhas',
          action: 'EXPORT',
          resource: 'payments',
          httpMethod: 'GET',
          path: '/api/admin/payments/export',
          status: 200,
          details: 'format=csv',
          occurredAt: '2027-01-10T15:00:00Z',
        },
      ]),
    );
    await settle(fixture);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('admin.familhas');
    expect(text).toContain('Exportou');
    expect(text).toContain('Pagamentos');
    expect(text).toContain('format=csv');
  });
});
