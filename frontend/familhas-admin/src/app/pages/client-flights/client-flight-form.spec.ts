import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG, AppConfig } from '../../core/config/app-config';
import { ClientFlightFormPage } from './client-flight-form.page';

describe('Formulário de voo', () => {
  it('converte o horário local e manda CPF em branco como null (mantém o guardado)', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: 'http://api' } as AppConfig },
      ],
    });
    const fixture = TestBed.createComponent(ClientFlightFormPage);
    fixture.detectChanges();
    const page = fixture.componentInstance as unknown as {
      form: {
        patchValue(v: object): void;
        controls: { travelers: { at(i: number): { patchValue(v: object): void } } };
      };
      request(): Record<string, unknown> & { travelers: Record<string, unknown>[] };
    };
    page.form.patchValue({
      airline: ' LATAM ',
      origin: 'BSB',
      destination: 'MCZ',
      outboundAt: '2026-12-10T08:30',
      locator: ' abc123 ',
    });
    page.form.controls.travelers.at(0).patchValue({ id: 't1', fullName: ' Ana ', cpf: '  ' });
    const body = page.request();
    expect(body['outboundAt']).toBe('2026-12-10T08:30:00');
    expect(body['returnAt']).toBeNull();
    expect(body['airline']).toBe('LATAM');
    expect(body['locator']).toBe('abc123');
    expect(body.travelers[0]).toEqual({ id: 't1', fullName: 'Ana', cpf: null, phone: null });
  });
});
