import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG, AppConfig } from '../../core/config/app-config';
import { CrmSession } from '../../core/crm/crm-session';
import { missingFields } from './voucher-fields';
import { VoucherFormPage } from './voucher-form.page';

describe('Emitir Voucher', () => {
  it('campos obrigatórios por tipo, iguais aos da API', () => {
    expect(missingFields('HOTEL', { provider: 'Hotel X' })).toEqual(['Check-in', 'Check-out']);
    expect(missingFields('TOUR', { description: ' ', serviceDate: '2027-01-30' })).toEqual([
      'Passeio',
    ]);
    expect(
      missingFields('FLIGHT', {
        provider: 'LATAM',
        origin: 'BSB',
        destination: 'MCZ',
        serviceDate: '2027-01-29',
      }),
    ).toEqual([]);
  });

  describe('formulário', () => {
    function create(aiConfigured: boolean) {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: APP_CONFIG, useValue: { apiBaseUrl: 'http://api' } as AppConfig },
          {
            provide: CrmSession,
            useValue: {
              ready: () => Promise.resolve(),
              integrations: () => [
                { key: 'AI_TEXT', label: 'IA', configured: aiConfigured, detail: '' },
              ],
            },
          },
        ],
      });
      const fixture = TestBed.createComponent(VoucherFormPage);
      fixture.detectChanges();
      return fixture;
    }

    it('sem IA configurada avisa e não mostra o botão de extração', () => {
      const text = (create(false).nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('A extração por IA não está configurada');
      expect(text).not.toContain('Extrair dados com IA');
      expect(text).toContain('Dados do cliente (sempre preenchidos à mão)');
    });

    it('manda só os campos do tipo escolhido e CPF em branco como null', () => {
      const page = create(false).componentInstance as unknown as {
        type: { set(v: string): void };
        details: { set(v: object): void };
        clientName: string;
        clientCpf: string;
        passengers: string;
        request(): Record<string, unknown> & { details: Record<string, unknown> };
      };
      page.type.set('HOTEL');
      page.details.set({
        provider: 'Hotel X',
        serviceDate: '2027-01-29',
        endDate: '2027-02-04',
        flightNumber: 'LA1',
      });
      page.clientName = '  Maria  ';
      page.clientCpf = ' ';
      page.passengers = 'João\n\n Ana ';
      const body = page.request();
      expect(body['clientName']).toBe('Maria');
      expect(body['clientCpf']).toBeNull();
      expect(body.details['flightNumber']).toBeUndefined();
      expect(body.details['provider']).toBe('Hotel X');
      expect(body.details['passengers']).toEqual(['João', 'Ana']);
    });
  });
});
