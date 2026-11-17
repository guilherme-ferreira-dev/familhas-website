import { firstValueFrom } from 'rxjs';
import type { CrmApi } from '../../core/crm/crm-api';
import { loadImage } from './canvas';

/**
 * Imagem de fundo por IA (OpenAI, ADR 0019). A API guarda a imagem como arquivo do CRM; aqui ela vira um
 * HTMLImageElement com CORS para o canvas exportar. Logo e textos continuam compostos no navegador.
 */
export async function generatePostImage(
  api: CrmApi,
  subject: 'CITY' | 'SHIP',
  name: string,
): Promise<HTMLImageElement> {
  const file = await firstValueFrom(api.generateImage(subject, name.trim()));
  const link = await firstValueFrom(api.fileLink(file.id));
  return loadImage(api.absolute(link.path));
}
