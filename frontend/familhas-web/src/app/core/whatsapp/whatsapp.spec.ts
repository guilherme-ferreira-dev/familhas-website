import { CurrencyPipe, registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { buildWhatsappUrl, shortId, whatsappMessage, WhatsappContextService } from './whatsapp';

registerLocaleData(localePt);
const currency = new CurrencyPipe('pt-BR', 'BRL');
const SITE = 'https://familhas.com.br';

describe('buildWhatsappUrl', () => {
  it('monta o link wa.me com a mensagem codificada', () => {
    expect(buildWhatsappUrl('5561999999999', 'Olá & tchau?')).toBe(
      'https://wa.me/5561999999999?text=Ol%C3%A1%20%26%20tchau%3F',
    );
  });

  it('sem número configurado não há link', () => {
    expect(buildWhatsappUrl('', 'oi')).toBeNull();
  });
});

describe('whatsappMessage', () => {
  it('usa a saudação padrão sem contexto', () => {
    expect(whatsappMessage(null, 'Oi!', SITE, currency)).toBe('Oi!');
  });

  it('cita o pacote com link', () => {
    const msg = whatsappMessage(
      { kind: 'package', id: 'p1', title: 'Maceió' },
      'Oi!',
      SITE,
      currency,
    );
    expect(msg).toContain('"Maceió"');
    expect(msg).toContain(`${SITE}/pacotes/p1`);
  });

  it('cita a cotação com código curto e total em reais', () => {
    const msg = whatsappMessage(
      { kind: 'quote', id: 'abcdef12-3456', total: 7830.16, currency: 'BRL' },
      'Oi!',
      SITE,
      currency,
    );
    expect(msg).toContain('ABCDEF12');
    expect(msg).toMatch(/R\$\s7\.830,16/);
  });

  it('cita a reserva pelo código curto', () => {
    expect(
      whatsappMessage({ kind: 'booking', id: '8d0e4c7a-31b2' }, 'Oi!', SITE, currency),
    ).toContain('8D0E4C7A');
  });

  it('código curto tem 8 caracteres', () => {
    expect(shortId('8d0e4c7a-31b2-4f0f')).toBe('8D0E4C7A');
  });
});

describe('WhatsappContextService', () => {
  it('limpa o contexto quando a rota muda', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'x', children: [] }])] });
    const service = TestBed.inject(WhatsappContextService);
    service.set({ kind: 'booking', id: 'b1' });
    await TestBed.inject(Router).navigateByUrl('/x');
    expect(service.context()).toBeNull();
  });
});
