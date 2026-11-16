import { BRAND, Ctx, logo, photo, roundRect, text } from './canvas';

export type SlideFormat = 'STORY' | 'FEED' | 'REEL';

export function slideSize(format: SlideFormat): { width: number; height: number } {
  return format === 'FEED' ? { width: 1080, height: 1080 } : { width: 1080, height: 1920 };
}

/** Rodapé fixo do JSON. O WhatsApp vem dos dados da agência. */
export function footerText(whatsapp: string): string {
  return `Planeje sua viagem com a Voe Familhas • WhatsApp ${whatsapp}`;
}

export interface SlideData {
  readonly image: HTMLImageElement | null;
  readonly hook: string;
  readonly title: string;
  readonly bullets: readonly string[];
  readonly logo: HTMLImageElement | null;
  readonly footer: string;
}

/** Cópia "-limpa": só a foto no formato, sem textos, para reescrever depois sem gerar a imagem de novo. */
export function drawCleanSlide(
  ctx: Ctx,
  format: SlideFormat,
  image: HTMLImageElement | null,
): void {
  const { width, height } = slideSize(format);
  photo(ctx, image, 0, 0, width, height);
}

/** Lâmina com marca: logo no topo, gancho em destaque, título, 3 a 5 tópicos e o rodapé com o WhatsApp. */
export function drawBrandedSlide(ctx: Ctx, format: SlideFormat, d: SlideData): void {
  const { width: W, height: H } = slideSize(format);
  drawCleanSlide(ctx, format, d.image);
  const top = ctx.createLinearGradient(0, 0, 0, H * 0.3);
  top.addColorStop(0, 'rgba(11,27,69,0.75)');
  top.addColorStop(1, 'rgba(11,27,69,0)');
  ctx.fillStyle = top;
  ctx.fillRect(0, 0, W, H * 0.3);
  const bottom = ctx.createLinearGradient(0, H * 0.4, 0, H);
  bottom.addColorStop(0, 'rgba(11,27,69,0)');
  bottom.addColorStop(1, 'rgba(11,27,69,0.92)');
  ctx.fillStyle = bottom;
  ctx.fillRect(0, H * 0.4, W, H * 0.6);

  logo(ctx, d.logo, W / 2, format === 'FEED' ? 36 : 110, 300, 110, 'center');

  const footerH = 84;
  const blockBottom = H - footerH - 40;
  const bulletLines = d.bullets.filter((b) => b.trim()).slice(0, 5);
  const estimated = (d.hook ? 110 : 0) + (d.title ? 140 : 0) + bulletLines.length * 60;
  let y = Math.max(H * 0.35, blockBottom - estimated);
  if (d.hook) {
    ctx.font = '800 44px sans-serif';
    const width = Math.min(W - 96, ctx.measureText(d.hook.toUpperCase()).width * 1.1 + 56);
    roundRect(ctx, 48, y, width, 80, 16, BRAND.yellow);
    text(ctx, d.hook.toUpperCase(), 76, y + 16, {
      size: 44,
      weight: 800,
      color: BRAND.night,
      maxWidth: width - 56,
      maxLines: 1,
    });
    y += 110;
  }
  if (d.title)
    y +=
      text(ctx, d.title, 48, y, {
        size: 58,
        weight: 800,
        color: BRAND.white,
        maxWidth: W - 96,
        maxLines: 2,
      }) + 20;
  for (const bullet of bulletLines) {
    y +=
      text(ctx, `• ${bullet}`, 48, y, {
        size: 34,
        weight: 500,
        color: BRAND.white,
        maxWidth: W - 96,
        maxLines: 2,
      }) + 14;
  }
  ctx.fillStyle = BRAND.yellow;
  ctx.fillRect(0, H - footerH, W, footerH);
  text(ctx, d.footer, W / 2, H - footerH + 24, {
    size: 30,
    weight: 700,
    color: BRAND.night,
    align: 'center',
    maxWidth: W - 60,
    maxLines: 1,
  });
}
