import type { InstagramPostRequest, PostFormat } from '../../core/api/crm-types';

/** Lâmina em edição. */
export interface SlideDraft {
  imageFileId: string;
  cleanFileId: string | null;
  previewUrl: string | null;
  hook: string;
  title: string;
  bullets: string;
  caption: string;
}

/** Problemas do post antes de salvar (a API confere de novo). */
export function postProblems(
  format: PostFormat,
  slides: readonly SlideDraft[],
  hasVideo: boolean,
  hashtags: string,
): string[] {
  const problems: string[] = [];
  if (format === 'STORY' && (slides.length !== 1 || hasVideo))
    problems.push('Story leva exatamente 1 imagem.');
  if (format === 'FEED' && (slides.length < 1 || slides.length > 20 || hasVideo))
    problems.push('Feed leva de 1 a 20 imagens (2 ou mais viram carrossel).');
  if (format === 'REEL' && (!hasVideo || slides.length > 1))
    problems.push('Reel leva um vídeo MP4 e, se quiser, uma capa.');
  const tags = hashtags.split(/\s+/).filter((t) => t.startsWith('#') && t.length > 1).length;
  if (tags > 30) problems.push('No máximo 30 hashtags.');
  slides.forEach((s, i) => {
    const bullets = s.bullets.split('\n').filter((b) => b.trim());
    if (bullets.length > 5) problems.push(`Lâmina ${i + 1}: no máximo 5 tópicos.`);
  });
  return problems;
}

/** Legenda + hashtags, como vai para o Instagram. */
export function fullCaption(caption: string, hashtags: string): string {
  return hashtags.trim() ? `${caption.trim()}\n\n${hashtags.trim()}` : caption.trim();
}

/** Arquivo de legendas do ZIP: a do post e a própria de cada lâmina. */
export function captionsFile(
  title: string,
  caption: string,
  hashtags: string,
  slides: readonly SlideDraft[],
): string {
  const parts = [`# ${title}`, '', fullCaption(caption, hashtags)];
  slides.forEach((s, i) => {
    if (s.caption.trim()) parts.push('', `## Lâmina ${i + 1}`, s.caption.trim());
  });
  return parts.join('\n') + '\n';
}

export function toRequest(input: {
  format: PostFormat;
  title: string;
  idea: string;
  caption: string;
  hashtags: string;
  slides: readonly SlideDraft[];
  videoFileId: string | null;
  version: number | null;
  origin?: InstagramPostRequest['origin'];
}): InstagramPostRequest {
  return {
    format: input.format,
    origin: input.origin ?? null,
    title: input.title.trim(),
    idea: input.idea.trim() || null,
    caption: input.caption.trim() || null,
    hashtags: input.hashtags.trim() || null,
    slides: input.slides.map((s) => ({
      imageFileId: s.imageFileId,
      cleanFileId: s.cleanFileId,
      hook: s.hook.trim() || null,
      title: s.title.trim() || null,
      bullets: s.bullets
        .split('\n')
        .map((b) => b.trim())
        .filter(Boolean),
      caption: s.caption.trim() || null,
    })),
    videoFileId: input.videoFileId,
    version: input.version,
  };
}
