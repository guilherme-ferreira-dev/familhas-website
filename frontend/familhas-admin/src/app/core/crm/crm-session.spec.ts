import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { CrmMe, CrmSettings } from '../api/crm-types';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { CrmSession } from './crm-session';

const API = 'http://localhost:8080';

const ME: CrmMe = {
  subject: 's1',
  username: 'atendente.crm',
  name: 'Atendente',
  email: null,
  roles: ['ATENDENTE'],
  permissions: ['crm:dashboard:ler', 'crm:vouchers:criar'],
};

const SETTINGS: CrmSettings = {
  company: {
    legalName: 'EEBPG Agência de Viagens LTDA',
    tradeName: 'Voe Familhas',
    cnpj: '62.845.580/0001-75',
    site: 'familhas.tur.br',
    whatsapp: '(61) 99990-9464',
    email: '',
    instagram: '',
  },
  integrations: [
    {
      key: 'AI_TEXT',
      label: 'IA de texto',
      configured: false,
      detail: 'Provedor ainda nao definido.',
    },
  ],
};

describe('CrmSession', () => {
  let session: CrmSession;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiBaseUrl: API } as AppConfig },
      ],
    });
    session = TestBed.inject(CrmSession);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function load(me: CrmMe = ME) {
    const ready = session.ready();
    backend.expectOne(`${API}/api/crm/me`).flush(me);
    await Promise.resolve();
    await Promise.resolve();
    if (me.permissions.includes('crm:dashboard:ler')) {
      backend.expectOne(`${API}/api/crm/settings`).flush(SETTINGS);
    }
    await ready;
  }

  it('carrega perfil e dados da agência uma vez só', async () => {
    await load();
    expect(session.can('crm:vouchers:criar')).toBe(true);
    expect(session.can('crm:vouchers:excluir')).toBe(false);
    expect(session.company()?.cnpj).toBe('62.845.580/0001-75');
    expect(session.integrations()).toHaveLength(1);
    await session.ready();
    backend.expectNone(`${API}/api/crm/me`);
  });

  it('logout limpa tudo e o próximo login carrega de novo', async () => {
    await load();
    session.clear();
    expect(session.can('crm:vouchers:criar')).toBe(false);
    expect(session.company()).toBeNull();
    await load();
    expect(session.profile()?.username).toBe('atendente.crm');
  });

  it('falha no perfil deixa sem permissões e permite tentar de novo', async () => {
    const ready = session.ready();
    backend.expectOne(`${API}/api/crm/me`).flush({}, { status: 500, statusText: 'erro' });
    await ready;
    expect(session.loadFailed()).toBe(true);
    expect(session.can('crm:dashboard:ler')).toBe(false);
    await load();
    expect(session.can('crm:dashboard:ler')).toBe(true);
  });
});
