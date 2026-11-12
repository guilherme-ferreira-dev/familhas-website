import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { APP_CONFIG, AppConfig } from './core/config/app-config';
import { signal } from '@angular/core';

describe('App (shell)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: APP_CONFIG,
          useValue: {
            siteUrl: 'http://localhost:4200',
            whatsapp: { phone: '5561999999999', defaultMessage: 'Olá!' },
          } as AppConfig,
        },
        {
          provide: AuthService,
          useValue: { user: signal(null), isAuthenticated: signal(false), login: vi.fn() },
        },
      ],
    }).compileComponents();
  });

  it('tem link para pular ao conteúdo, cabeçalho, main e o botão do WhatsApp', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('a.skip-link')?.getAttribute('href')).toBe('#conteudo');
    expect(el.querySelector('main#conteudo')).not.toBeNull();
    expect(el.querySelector('nav[aria-label="Principal"]')).not.toBeNull();
    expect(el.querySelector('a[href^="https://wa.me/"]')).not.toBeNull();
    expect(el.textContent).toContain('Entrar');
  });
});
