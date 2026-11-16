import { BRAND, Ctx, logo, photo, roundRect, text } from './canvas';

export type CruiseLayoutId = 'card-oceano' | 'oportunidade';
export type CruiseFormat = 'FEED' | 'STORY';

export const CRUISE_LAYOUTS: readonly { id: CruiseLayoutId; label: string }[] = [
  { id: 'card-oceano', label: 'Card no oceano' },
  { id: 'oportunidade', label: 'Oportunidade' },
];

export function cruiseSize(format: CruiseFormat): { width: number; height: number } {
  return format === 'STORY' ? { width: 1080, height: 1920 } : { width: 1080, height: 1080 };
}

export interface CruisePostData {
  readonly headline: string;
  readonly ship: string;
  readonly itinerary: string;
  readonly dates: string;
  readonly cabin: string;
  readonly price: string;
  readonly installments: string;
  readonly inclusions: readonly string[];
  readonly image: HTMLImageElement | null;
  readonly logo: HTMLImageElement | null;
}

/** Posts de cruzeiro no estilo dos de cotação. A chamada é opcional (o JSON removeu "OPORTUNIDADE DE CRUZEIRO"). */
export function drawCruisePost(
  ctx: Ctx,
  layout: CruiseLayoutId,
  format: CruiseFormat,
  d: CruisePostData,
): void {
  const { width: W, height: H } = cruiseSize(format);
  const story = format === 'STORY';
  ctx.clearRect(0, 0, W, H);
  if (layout === 'card-oceano') {
    const g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, '#7fd3f0');
    g.addColorStop(1, BRAND.ocean);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
    logo(ctx, d.logo, W / 2, story ? 90 : 36, 300, 110, 'center');
    const photoTop = story ? 280 : 170;
    const photoH = story ? 760 : 430;
    roundRect(ctx, 60, photoTop - 10, W - 120, photoH + 20, 32, BRAND.white);
    photo(ctx, d.image, 70, photoTop, W - 140, photoH);
    let y = photoTop + photoH + (story ? 60 : 30);
    if (d.headline)
      y +=
        text(ctx, d.headline.toUpperCase(), W / 2, y, {
          size: 34,
          weight: 800,
          color: BRAND.yellow,
          align: 'center',
          maxWidth: W - 120,
          maxLines: 1,
        }) + 6;
    y += text(ctx, d.ship, W / 2, y, {
      size: story ? 64 : 52,
      weight: 800,
      color: BRAND.white,
      align: 'center',
      maxWidth: W - 120,
      maxLines: 2,
    });
    y +=
      text(ctx, d.itinerary, W / 2, y + 6, {
        size: 28,
        color: BRAND.white,
        align: 'center',
        maxWidth: W - 160,
        maxLines: story ? 3 : 2,
      }) + 10;
    y +=
      text(ctx, [d.dates, d.cabin].filter(Boolean).join(' · '), W / 2, y, {
        size: 26,
        color: BRAND.white,
        align: 'center',
        maxWidth: W - 160,
      }) + (story ? 40 : 14);
    if (story)
      y +=
        text(ctx, d.inclusions.map((i) => `✓ ${i}`).join('\n'), W / 2, y, {
          size: 28,
          color: BRAND.white,
          align: 'center',
          maxWidth: W - 200,
          maxLines: 6,
        }) + 40;
    price(
      ctx,
      d,
      W / 2,
      story ? Math.max(y, 1560) : Math.min(y, 900),
      W - 160,
      BRAND.yellow,
      BRAND.white,
    );
  } else {
    photo(ctx, d.image, 0, 0, W, H);
    logo(ctx, d.logo, 48, story ? 80 : 40, 280, 110, 'left');
    const panelTop = story ? 1050 : 560;
    ctx.fillStyle = 'rgba(255,255,255,0.95)';
    ctx.fillRect(0, panelTop, W, H - panelTop);
    let y = panelTop + 30;
    if (d.headline) {
      roundRect(ctx, 48, y, Math.min(W - 96, 40 + d.headline.length * 22), 56, 10, BRAND.yellow);
      text(ctx, d.headline.toUpperCase(), 68, y + 10, {
        size: 32,
        weight: 800,
        color: BRAND.night,
        maxWidth: W - 140,
        maxLines: 1,
      });
      y += 76;
    }
    y += text(ctx, d.ship, 48, y, {
      size: story ? 60 : 50,
      weight: 800,
      maxWidth: W - 96,
      maxLines: 2,
    });
    y +=
      text(ctx, d.itinerary, 48, y + 6, {
        size: 28,
        color: BRAND.muted,
        maxWidth: W - 96,
        maxLines: story ? 3 : 2,
      }) + 8;
    y +=
      text(ctx, [d.dates, d.cabin].filter(Boolean).join(' · '), 48, y, {
        size: 26,
        color: BRAND.muted,
        maxWidth: 560,
      }) + 10;
    if (story)
      y +=
        text(ctx, d.inclusions.map((i) => `✓ ${i}`).join('\n'), 48, y + 10, {
          size: 28,
          maxWidth: W - 96,
          maxLines: 6,
        }) + 30;
    price(
      ctx,
      d,
      story ? W / 2 : 840,
      story ? Math.max(y, 1580) : panelTop + 60,
      story ? W - 160 : 380,
      BRAND.night,
      BRAND.night,
    );
  }
}

function price(
  ctx: Ctx,
  d: CruisePostData,
  cx: number,
  y: number,
  w: number,
  accent: string,
  color: string,
): void {
  if (!d.price) return;
  y += text(ctx, 'Por pessoa', cx, y, {
    size: 24,
    weight: 600,
    color,
    align: 'center',
    maxWidth: w,
  });
  y += text(ctx, d.price, cx, y + 4, {
    size: 70,
    weight: 800,
    color: accent,
    align: 'center',
    maxWidth: w,
    maxLines: 1,
  });
  text(ctx, d.installments, cx, y + 10, {
    size: 26,
    weight: 700,
    color,
    align: 'center',
    maxWidth: w,
    maxLines: 2,
  });
}
