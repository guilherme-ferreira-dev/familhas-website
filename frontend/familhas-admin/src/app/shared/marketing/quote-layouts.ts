import { BRAND, Ctx, cover, logo, placeholder, roundRect, text } from './canvas';

export type QuoteLayoutId =
  'classico' | 'coluna-lateral' | 'card-central' | 'faixa' | 'destaque-preco';

export const QUOTE_LAYOUTS: readonly { id: QuoteLayoutId; label: string }[] = [
  { id: 'classico', label: 'Clássico' },
  { id: 'coluna-lateral', label: 'Coluna lateral' },
  { id: 'card-central', label: 'Card central' },
  { id: 'faixa', label: 'Faixa inferior' },
  { id: 'destaque-preco', label: 'Destaque no preço' },
];

export const POST_SIZE = 1080;

export interface QuotePostData {
  readonly destination: string;
  readonly dates: string;
  readonly lines: readonly string[];
  readonly price: string;
  readonly priceLabel: string;
  readonly payment: string;
  readonly observation: string;
  readonly headline: string;
  readonly image: HTMLImageElement | null;
  readonly logo: HTMLImageElement | null;
}

/** Desenha o post de cotação 1080x1080 no layout escolhido. Pagamento sempre centralizado sob o valor. */
export function drawQuotePost(ctx: Ctx, layout: QuoteLayoutId, d: QuotePostData): void {
  const S = POST_SIZE;
  ctx.clearRect(0, 0, S, S);
  const photo = (x: number, y: number, w: number, h: number) =>
    d.image ? cover(ctx, d.image, x, y, w, h) : placeholder(ctx, x, y, w, h);

  switch (layout) {
    case 'classico': {
      photo(0, 0, S, 600);
      ctx.fillStyle = BRAND.white;
      ctx.fillRect(0, 600, S, S - 600);
      logo(ctx, d.logo, 48, 40, 260, 120, 'left');
      headline(ctx, d, 48, 470, S - 96, BRAND.white, true);
      let y = 630;
      y += text(ctx, d.destination.toUpperCase(), 48, y, {
        size: 64,
        weight: 800,
        maxWidth: 560,
        maxLines: 2,
      });
      y +=
        text(ctx, d.dates, 48, y + 6, {
          size: 34,
          weight: 600,
          color: BRAND.muted,
          maxWidth: 560,
        }) + 20;
      bullets(ctx, d.lines, 48, y, 560, BRAND.ink, 28, 6);
      priceBlock(ctx, d, 820, 650, 400, BRAND.ink);
      text(ctx, d.observation, S / 2, S - 54, {
        size: 26,
        color: BRAND.muted,
        align: 'center',
        maxWidth: S - 96,
      });
      break;
    }
    case 'coluna-lateral': {
      const col = 430;
      photo(col, 0, S - col, S);
      ctx.fillStyle = BRAND.night;
      ctx.fillRect(0, 0, col, S);
      logo(ctx, d.logo, col / 2, 40, 300, 120, 'center');
      headline(ctx, d, col + 40, 48, S - col - 80, BRAND.white, true);
      let y = 200;
      y += text(ctx, d.destination.toUpperCase(), 36, y, {
        size: 56,
        weight: 800,
        color: BRAND.white,
        maxWidth: col - 72,
        maxLines: 3,
      });
      y +=
        text(ctx, d.dates, 36, y + 8, { size: 30, color: BRAND.yellow, maxWidth: col - 72 }) + 30;
      y = bullets(ctx, d.lines, 36, y, col - 72, BRAND.white, 26, 7) + 20;
      priceBlock(ctx, d, col / 2, Math.max(y, 700), col - 40, BRAND.white);
      text(ctx, d.observation, col / 2, S - 50, {
        size: 22,
        color: BRAND.white,
        align: 'center',
        maxWidth: col - 40,
      });
      break;
    }
    case 'card-central': {
      photo(0, 0, S, S);
      logo(ctx, d.logo, S / 2, 40, 300, 120, 'center');
      roundRect(ctx, 110, 300, S - 220, 700, 36, 'rgba(255,255,255,0.95)');
      headline(ctx, d, S / 2, 220, S - 220, BRAND.white, true, 'center');
      let y = 340;
      y += text(ctx, d.destination.toUpperCase(), S / 2, y, {
        size: 60,
        weight: 800,
        align: 'center',
        maxWidth: S - 300,
        maxLines: 2,
      });
      y +=
        text(ctx, d.dates, S / 2, y + 6, {
          size: 32,
          color: BRAND.muted,
          align: 'center',
          maxWidth: S - 300,
        }) + 24;
      y = bullets(ctx, d.lines, 170, y, S - 340, BRAND.ink, 28, 5) + 16;
      priceBlock(ctx, d, S / 2, Math.max(y, 760), S - 300, BRAND.ink);
      text(ctx, d.observation, S / 2, 1030, {
        size: 24,
        color: BRAND.white,
        align: 'center',
        maxWidth: S - 96,
      });
      break;
    }
    case 'faixa': {
      photo(0, 0, S, S);
      logo(ctx, d.logo, 48, 40, 280, 120, 'left');
      headline(ctx, d, 48, 200, S - 96, BRAND.white, true);
      ctx.fillStyle = 'rgba(11,27,69,0.92)';
      ctx.fillRect(0, 560, S, S - 560);
      let y = 590;
      y += text(ctx, d.destination.toUpperCase(), 48, y, {
        size: 58,
        weight: 800,
        color: BRAND.white,
        maxWidth: 600,
        maxLines: 2,
      });
      y += text(ctx, d.dates, 48, y + 6, { size: 30, color: BRAND.yellow, maxWidth: 600 }) + 20;
      bullets(ctx, d.lines, 48, y, 600, BRAND.white, 26, 6);
      roundRect(ctx, 680, 600, 360, 330, 28, BRAND.yellow);
      priceBlock(ctx, d, 860, 630, 320, BRAND.night);
      text(ctx, d.observation, S / 2, S - 50, {
        size: 24,
        color: BRAND.white,
        align: 'center',
        maxWidth: S - 96,
      });
      break;
    }
    case 'destaque-preco': {
      photo(0, 0, S, S);
      ctx.fillStyle = 'rgba(11,27,69,0.55)';
      ctx.fillRect(0, 0, S, S);
      logo(ctx, d.logo, S / 2, 40, 300, 120, 'center');
      headline(ctx, d, S / 2, 190, S - 120, BRAND.yellow, false, 'center');
      text(ctx, d.destination.toUpperCase(), S / 2, 260, {
        size: 76,
        weight: 800,
        color: BRAND.white,
        align: 'center',
        maxWidth: S - 120,
        maxLines: 2,
      });
      text(ctx, d.dates, S / 2, 440, {
        size: 36,
        color: BRAND.white,
        align: 'center',
        maxWidth: S - 120,
      });
      priceBlock(ctx, d, S / 2, 510, S - 200, BRAND.yellow, 1.3);
      text(ctx, d.lines.join(' · '), S / 2, 820, {
        size: 28,
        color: BRAND.white,
        align: 'center',
        maxWidth: S - 140,
        maxLines: 3,
      });
      text(ctx, d.observation, S / 2, S - 54, {
        size: 26,
        color: BRAND.white,
        align: 'center',
        maxWidth: S - 96,
      });
      break;
    }
  }
}

function headline(
  ctx: Ctx,
  d: QuotePostData,
  x: number,
  y: number,
  w: number,
  color: string,
  box: boolean,
  align: CanvasTextAlign = 'left',
): void {
  if (!d.headline) return;
  ctx.font = `800 36px sans-serif`;
  if (box) {
    const width = Math.min(w, ctx.measureText(d.headline.toUpperCase()).width * 1.15 + 48);
    const left = align === 'center' ? x - width / 2 : x;
    roundRect(ctx, left, y - 10, width, 64, 12, BRAND.yellow);
    text(ctx, d.headline.toUpperCase(), align === 'center' ? x : x + 24, y, {
      size: 36,
      weight: 800,
      color: BRAND.night,
      align,
      maxWidth: width - 24,
      maxLines: 1,
    });
  } else {
    text(ctx, d.headline.toUpperCase(), x, y, {
      size: 36,
      weight: 800,
      color,
      align,
      maxWidth: w,
      maxLines: 1,
    });
  }
}

function bullets(
  ctx: Ctx,
  lines: readonly string[],
  x: number,
  y: number,
  w: number,
  color: string,
  size: number,
  max: number,
): number {
  for (const line of lines.slice(0, max)) {
    y += text(ctx, `✓ ${line}`, x, y, { size, weight: 500, color, maxWidth: w, maxLines: 2 }) + 8;
  }
  return y;
}

/** Valor por pessoa, rótulo e o texto de pagamento centralizado logo abaixo do valor. */
function priceBlock(
  ctx: Ctx,
  d: QuotePostData,
  cx: number,
  y: number,
  w: number,
  color: string,
  scale = 1,
): void {
  if (!d.price) return;
  y += text(ctx, d.priceLabel, cx, y, {
    size: 24 * scale,
    weight: 600,
    color,
    align: 'center',
    maxWidth: w,
  });
  y += text(ctx, d.price, cx, y + 4, {
    size: 72 * scale,
    weight: 800,
    color,
    align: 'center',
    maxWidth: w,
    maxLines: 1,
  });
  text(ctx, d.payment, cx, y + 12, {
    size: 26 * scale,
    weight: 700,
    color,
    align: 'center',
    maxWidth: w,
    maxLines: 2,
  });
}
