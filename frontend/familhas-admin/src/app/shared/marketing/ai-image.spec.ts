import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CrmApi } from '../../core/crm/crm-api';
import { generatePostImage } from './ai-image';

/** Image do navegador que "carrega" na hora, para o teste não depender de rede. */
class LoadedImage {
  crossOrigin: string | null = null;
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  set src(value: string) {
    this.loadedFrom = value;
    queueMicrotask(() => this.onload?.());
  }
  loadedFrom = '';
}

describe('imagem do post por IA', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('gera na API, pede o link assinado e carrega com CORS para o canvas', async () => {
    vi.stubGlobal('Image', LoadedImage);
    const api = {
      generateImage: vi.fn(() => of({ id: 'f-1' })),
      fileLink: vi.fn(() => of({ path: '/api/crm/files/f-1/content?sig=x' })),
      absolute: (path: string) => `http://api.test${path}`,
    } as unknown as CrmApi;

    const img = (await generatePostImage(api, 'CITY', '  Maceió ')) as unknown as LoadedImage;

    expect(api.generateImage).toHaveBeenCalledWith('CITY', 'Maceió');
    expect(api.fileLink).toHaveBeenCalledWith('f-1');
    expect(img.crossOrigin).toBe('anonymous');
    expect(img.loadedFrom).toBe('http://api.test/api/crm/files/f-1/content?sig=x');
  });

  it('erro da API (ex.: recusa ou limite por hora) chega à tela sem pedir link', async () => {
    const api = {
      generateImage: vi.fn(() => throwError(() => new Error('AI_IMAGE_QUOTA'))),
      fileLink: vi.fn(),
    } as unknown as CrmApi;

    await expect(generatePostImage(api, 'SHIP', 'MSC Seaview')).rejects.toThrow('AI_IMAGE_QUOTA');
    expect(api.fileLink).not.toHaveBeenCalled();
  });
});
