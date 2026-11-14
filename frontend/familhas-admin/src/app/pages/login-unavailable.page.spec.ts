import { render } from '../../testing/setup';
import { LoginUnavailablePage } from './login-unavailable.page';

describe('LoginUnavailablePage', () => {
  it('explica que o problema é o login, não a API', async () => {
    const { fixture } = await render(LoginUnavailablePage, { voltar: '/pagamentos' });

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Não foi possível falar com o login');
    expect(text).toContain('Keycloak');
    expect(text).not.toContain('API');
  });
});
