/** Utilitários de canvas para os posts. A logo oficial é sempre composta aqui, nunca desenhada por IA. */

export const BRAND = {
  night: '#0b1b45',
  yellow: '#ffc933',
  white: '#ffffff',
  ink: '#0b1b45',
  muted: '#4a5778',
  ocean: '#0a5f8a',
} as const;

export const FONT = '"Hanken Grotesk", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';

export type Ctx = CanvasRenderingContext2D;

/** Imagem de outra origem (link assinado da API) com CORS, para o canvas poder exportar. */
export function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Não foi possível carregar a imagem.'));
    img.src = url;
  });
}

export function fileToImage(file: Blob): Promise<HTMLImageElement> {
  const url = URL.createObjectURL(file);
  return loadImage(url).finally(() => setTimeout(() => URL.revokeObjectURL(url), 1000));
}

/** Desenha a imagem cobrindo o retângulo, cortando o excesso (como object-fit: cover). */
export function cover(
  ctx: Ctx,
  img: CanvasImageSource & { width: number; height: number },
  x: number,
  y: number,
  w: number,
  h: number,
): void {
  const scale = Math.max(w / img.width, h / img.height);
  const sw = w / scale;
  const sh = h / scale;
  ctx.drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, x, y, w, h);
}

/** Fundo do modelo quando ainda não há foto: gradiente da marca (sem seletor de cor, regra do JSON). */
export function placeholder(ctx: Ctx, x: number, y: number, w: number, h: number): void {
  const g = ctx.createLinearGradient(x, y, x + w, y + h);
  g.addColorStop(0, BRAND.ocean);
  g.addColorStop(1, BRAND.night);
  ctx.fillStyle = g;
  ctx.fillRect(x, y, w, h);
}

/** Foto cobrindo o retângulo, ou o fundo do modelo quando ainda não há foto. */
export function photo(
  ctx: Ctx,
  img: HTMLImageElement | null,
  x: number,
  y: number,
  w: number,
  h: number,
): void {
  if (img) cover(ctx, img, x, y, w, h);
  else placeholder(ctx, x, y, w, h);
}

export function wrap(ctx: Ctx, text: string, maxWidth: number): string[] {
  const lines: string[] = [];
  for (const paragraph of text.split('\n')) {
    let line = '';
    for (const word of paragraph.split(/\s+/).filter(Boolean)) {
      const candidate = line ? `${line} ${word}` : word;
      if (ctx.measureText(candidate).width > maxWidth && line) {
        lines.push(line);
        line = word;
      } else {
        line = candidate;
      }
    }
    lines.push(line);
  }
  return lines;
}

export interface TextStyle {
  readonly size: number;
  readonly weight?: number;
  readonly color?: string;
  readonly align?: CanvasTextAlign;
  readonly maxWidth: number;
  readonly lineHeight?: number;
  readonly maxLines?: number;
}

/** Escreve com quebra de linha. Devolve a altura usada. */
export function text(ctx: Ctx, value: string, x: number, y: number, style: TextStyle): number {
  if (!value) return 0;
  ctx.font = `${style.weight ?? 600} ${style.size}px ${FONT}`;
  ctx.fillStyle = style.color ?? BRAND.ink;
  ctx.textAlign = style.align ?? 'left';
  ctx.textBaseline = 'top';
  const lineHeight = style.lineHeight ?? style.size * 1.2;
  let lines = wrap(ctx, value, style.maxWidth);
  if (style.maxLines && lines.length > style.maxLines) {
    lines = lines.slice(0, style.maxLines);
    lines[lines.length - 1] = `${lines[lines.length - 1].replace(/\s*\S*$/, '')}…`;
  }
  lines.forEach((line, i) => ctx.fillText(line, x, y + i * lineHeight));
  return lines.length * lineHeight;
}

export function roundRect(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  fill: string,
): void {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
  ctx.fillStyle = fill;
  ctx.fill();
}

/**
 * Logo oficial dentro da caixa (mantém proporção). Sem logo enviada: a marca em texto ("Voe" em amarelo sobre
 * "Familhas"), nunca uma recriação do desenho.
 */
export function logo(
  ctx: Ctx,
  img: HTMLImageElement | null,
  x: number,
  y: number,
  maxW: number,
  maxH: number,
  align: 'left' | 'center' | 'right' = 'left',
  onDark = true,
): void {
  if (img) {
    const scale = Math.min(maxW / img.width, maxH / img.height);
    const w = img.width * scale;
    const h = img.height * scale;
    const left = align === 'left' ? x : align === 'center' ? x - w / 2 : x - w;
    ctx.drawImage(img, left, y, w, h);
    return;
  }
  const size = maxH * 0.55;
  const textAlign: CanvasTextAlign =
    align === 'left' ? 'left' : align === 'center' ? 'center' : 'right';
  text(ctx, 'Voe', x, y, {
    size: size * 0.55,
    weight: 800,
    color: BRAND.yellow,
    align: textAlign,
    maxWidth: maxW,
  });
  text(ctx, 'Familhas', x, y + size * 0.6, {
    size,
    weight: 800,
    color: onDark ? BRAND.white : BRAND.night,
    align: textAlign,
    maxWidth: maxW,
  });
}

export function toBlob(
  canvas: HTMLCanvasElement,
  type: 'image/png' | 'image/jpeg',
  quality = 0.92,
): Promise<Blob> {
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('Falha ao gerar a imagem.'))),
      type,
      quality,
    ),
  );
}

export function download(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Nome de arquivo seguro: "maceio-29-jan.png". */
export function slug(value: string): string {
  return (
    value
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .slice(0, 60) || 'post'
  );
}
