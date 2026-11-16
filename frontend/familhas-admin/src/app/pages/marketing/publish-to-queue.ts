import { firstValueFrom } from 'rxjs';
import type { InstagramPost, PostFormat, PostOrigin } from '../../core/api/crm-types';
import { CrmApi } from '../../core/crm/crm-api';
import { toBlob } from '../../shared/marketing/canvas';

/**
 * Leva um post pronto (canvas) para a fila do Instagram: JPEG (a Meta só aceita JPEG) enviado como INSTAGRAM_MEDIA
 * e um post em rascunho com a legenda. Publicar exige revisão na fila (marcar como pronto).
 */
export async function sendToInstagramQueue(
  api: CrmApi,
  canvas: HTMLCanvasElement,
  input: {
    format: PostFormat;
    origin: PostOrigin;
    title: string;
    caption: string;
    hashtags: string;
    fileName: string;
  },
): Promise<InstagramPost> {
  const jpeg = await toBlob(canvas, 'image/jpeg', 0.92);
  const file = new File([jpeg], `${input.fileName}.jpg`, { type: 'image/jpeg' });
  const stored = await firstValueFrom(api.uploadFile('INSTAGRAM_MEDIA', file));
  return firstValueFrom(
    api.createInstagramPost({
      format: input.format,
      origin: input.origin,
      title: input.title.slice(0, 120),
      caption: input.caption.slice(0, 2200),
      hashtags: input.hashtags,
      slides: [{ imageFileId: stored.id }],
    }),
  );
}
