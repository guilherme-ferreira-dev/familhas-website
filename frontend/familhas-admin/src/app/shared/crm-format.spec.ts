import type { Company, Voucher } from '../core/api/crm-types';
import {
  fromDateTimeInput,
  formatLocalDateTime,
  toDateTimeInput,
  voucherWhatsappText,
  whatsappLink,
  whatsappNumber,
} from './crm-format';

const COMPANY: Company = {
  legalName: 'EEBPG Agência de Viagens LTDA',
  tradeName: 'Voe Familhas',
  cnpj: '62.845.580/0001-75',
  site: 'familhas.tur.br',
  whatsapp: '(61) 99990-9464',
  email: '',
  instagram: '',
};

const VOUCHER: Voucher = {
  id: 'v1',
  number: 'VF-000007',
  type: 'HOTEL',
  status: 'ISSUED',
  clientName: 'Maria Cliente',
  clientCpfMasked: '***.982.247-**',
  clientPhone: '(61) 98888-7777',
  notes: 'Cliente pediu andar alto',
  details: {
    provider: 'Hotel Ponta Verde',
    serviceDate: '2027-01-29',
    endDate: '2027-02-04',
    board: 'Café da manhã',
  },
  issuedAt: '2026-11-16T15:00:00Z',
  version: 0,
};

describe('formatação do CRM', () => {
  it('horário do bilhete sem conversão de fuso', () => {
    expect(formatLocalDateTime('2026-12-10T08:30:00')).toBe('10/12/2026 08:30');
    expect(toDateTimeInput('2026-12-10T08:30:00')).toBe('2026-12-10T08:30');
    expect(fromDateTimeInput('2026-12-10T08:30')).toBe('2026-12-10T08:30:00');
    expect(fromDateTimeInput('')).toBeNull();
  });

  it('número do WhatsApp com DDI do Brasil', () => {
    expect(whatsappNumber('(61) 98888-7777')).toBe('5561988887777');
    expect(whatsappNumber('+55 61 98888-7777')).toBe('5561988887777');
    expect(whatsappNumber('123')).toBeNull();
    expect(whatsappLink(null, 'x')).toBeNull();
  });

  it('mensagem do voucher leva os dados do serviço e as observações, nunca o CPF', () => {
    const text = voucherWhatsappText(VOUCHER, COMPANY);
    expect(text).toContain('VF-000007');
    expect(text).toContain('Hotel Ponta Verde');
    expect(text).toContain('29/01/2027');
    expect(text).not.toContain('982.247');
    expect(text).toContain('Observações: Cliente pediu andar alto');
    const link = whatsappLink(VOUCHER.clientPhone, text)!;
    expect(link.startsWith('https://wa.me/5561988887777?text=')).toBe(true);
  });
});
