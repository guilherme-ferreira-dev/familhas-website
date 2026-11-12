import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { WhatsappContextService } from './whatsapp';
import { WhatsappButton } from './whatsapp-button';

function render(phone: string) {
  TestBed.configureTestingModule({
    imports: [WhatsappButton],
    providers: [
      provideRouter([]),
      {
        provide: APP_CONFIG,
        useValue: {
          siteUrl: 'http://localhost:4200',
          whatsapp: { phone, defaultMessage: 'Olá!' },
        } as AppConfig,
      },
    ],
  });
  const fixture = TestBed.createComponent(WhatsappButton);
  fixture.detectChanges();
  return fixture;
}

describe('WhatsappButton', () => {
  it('não aparece sem número configurado', () => {
    const fixture = render('');
    expect(fixture.nativeElement.querySelector('a')).toBeNull();
  });

  it('abre o wa.me em nova aba, com rótulo acessível e contexto da tela', async () => {
    const fixture = render('5561999999999');
    TestBed.inject(WhatsappContextService).set({ kind: 'booking', id: 'abcdef12-0000' });
    await fixture.whenStable();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    expect(link.href).toContain('https://wa.me/5561999999999?text=');
    expect(decodeURIComponent(link.href)).toContain('reserva ABCDEF12');
    expect(link.target).toBe('_blank');
    expect(link.rel).toContain('noopener');
    expect(link.getAttribute('aria-label')).toContain('WhatsApp');
  });
});
