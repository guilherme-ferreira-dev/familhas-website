import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { APP_CONFIG } from '../../core/config/app-config';
import { buildWhatsappUrl } from '../../core/whatsapp/whatsapp';
import { CatalogApi } from '../../core/catalog/catalog-api';
import { CityDirectory } from '../../core/catalog/city-directory';
import { LoadError } from '../../shared/load-error';
import { PackageCard } from '../../shared/package-card';

@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, LoadError, PackageCard],
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
})
export class HomePage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  protected readonly cities = inject(CityDirectory);
  private readonly config = inject(APP_CONFIG);
  protected readonly whatsappUrl = buildWhatsappUrl(
    this.config.whatsapp.phone,
    this.config.whatsapp.defaultMessage,
  );

  private readonly destroyRef = inject(DestroyRef);
  private readonly backdrop = viewChild.required<ElementRef<HTMLElement>>('backdrop');
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly hero = viewChild.required<ElementRef<HTMLElement>>('hero');

  protected readonly destination = signal('');
  protected readonly passengers = signal(2);
  protected readonly passengerOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  protected readonly offers = rxResource({
    stream: () => this.api.searchPackages({ size: 6, passengers: 1 }).pipe(map((p) => p.content)),
  });

  protected readonly categories = rxResource({
    stream: () => this.api.categories().pipe(map((p) => p.content)),
  });

  /** Praia pega o pôr do sol laranja; o resto alterna entre as demais paletas. */
  protected tileVariant(title: string, index: number): number {
    return /praia/i.test(title) ? 2 : index % 2 === 0 ? 3 : 1;
  }

  constructor() {
    afterNextRender(() => this.startScrollScene());
  }

  /**
   * Os quadros do clipe (public/hero/frames) são desenhados num canvas fixo e avançam conforme a
   * página desce. No topo aparecem dentro da "janela" do hero; ao rolar, a janela se abre até
   * cobrir a tela toda.
   */
  private startScrollScene(): void {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const backdrop = this.backdrop().nativeElement;
    const canvas = this.canvas().nativeElement;
    const hero = this.hero().nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const total = 60;
    const frames: (HTMLImageElement | undefined)[] = new Array(total);
    let current = 0;
    let raf = 0;
    const clamp = (n: number) => Math.min(1, Math.max(0, n));
    const ease = (t: number) => t * t * (3 - 2 * t);

    const draw = (index: number) => {
      // Usa o quadro mais próximo que já carregou.
      for (let d = 0; d < total; d++) {
        const img = frames[index - d] ?? frames[index + d];
        if (img?.complete && img.naturalWidth > 0) {
          const scale = Math.max(
            canvas.width / img.naturalWidth,
            canvas.height / img.naturalHeight,
          );
          const w = img.naturalWidth * scale;
          const h = img.naturalHeight * scale;
          ctx.drawImage(img, (canvas.width - w) / 2, (canvas.height - h) / 2, w, h);
          return;
        }
      }
    };

    const render = () => {
      raf = 0;
      const vw = innerWidth;
      const vh = innerHeight;
      const dpr = Math.min(devicePixelRatio || 1, 1.5);
      if (canvas.width !== Math.round(vw * dpr) || canvas.height !== Math.round(vh * dpr)) {
        canvas.width = Math.round(vw * dpr);
        canvas.height = Math.round(vh * dpr);
      }
      const y = scrollY;
      const rect = hero.getBoundingClientRect();
      const open = ease(clamp(y / Math.max(1, rect.height * 0.8)));
      const edge = (n: number) => Math.max(0, n) * (1 - open);
      backdrop.style.clipPath = `inset(${edge(rect.top)}px ${edge(vw - rect.right)}px ${edge(
        vh - rect.bottom,
      )}px ${edge(rect.left)}px round ${32 * (1 - open)}px)`;
      backdrop.style.setProperty('--dim', String(0.2 + 0.3 * open));

      const max = Math.max(1, document.documentElement.scrollHeight - vh);
      const target = clamp(y / max) * (total - 1);
      current += (target - current) * 0.2;
      if (Math.abs(target - current) < 0.02) current = target;
      draw(Math.round(current));
      if (current !== target) schedule();
    };
    const schedule = () => {
      if (!raf) raf = requestAnimationFrame(render);
    };

    for (let i = 0; i < total; i++) {
      const img = new Image();
      img.onload = schedule;
      img.src = `hero/frames/f-${String(i).padStart(3, '0')}.jpg`;
      frames[i] = img;
    }

    backdrop.classList.add('live');
    hero.classList.add('live');
    addEventListener('scroll', schedule, { passive: true });
    addEventListener('resize', schedule);
    this.destroyRef.onDestroy(() => {
      removeEventListener('scroll', schedule);
      removeEventListener('resize', schedule);
      cancelAnimationFrame(raf);
    });
    render();
  }

  protected search(): void {
    void this.router.navigate(['/busca'], {
      queryParams: {
        tipo: 'pacotes',
        destino: this.destination() || null,
        passageiros: this.passengers(),
      },
    });
  }
}
