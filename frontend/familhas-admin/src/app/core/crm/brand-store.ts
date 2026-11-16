import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { CrmFile } from '../api/crm-types';
import { loadImage } from '../../shared/marketing/canvas';
import { CrmApi } from './crm-api';

/**
 * Logo oficial para a lateral e para a composição dos posts. O link assinado vence em 5 minutos, então a imagem é
 * carregada uma vez (com CORS, para o canvas exportar) e reaproveitada na sessão.
 */
@Injectable({ providedIn: 'root' })
export class BrandStore {
  private readonly api = inject(CrmApi);
  private readonly file = signal<CrmFile | null>(null);
  private readonly url = signal<string | null>(null);
  private image: Promise<HTMLImageElement | null> | null = null;
  private loaded: Promise<void> | null = null;

  readonly logoFile = this.file.asReadonly();
  readonly logoUrl = this.url.asReadonly();

  ready(): Promise<void> {
    this.loaded ??= this.load();
    return this.loaded;
  }

  /** Imagem pronta para o canvas, ou null sem logo enviada (a composição usa a marca em texto). */
  async logoImage(): Promise<HTMLImageElement | null> {
    await this.ready();
    if (!this.url()) return null;
    this.image ??= loadImage(this.url()!).catch(() => null);
    return this.image;
  }

  async replace(file: File): Promise<void> {
    const brand = await firstValueFrom(this.api.replaceLogo(file));
    this.image = null;
    await this.apply(brand.logo ?? null);
  }

  clear(): void {
    this.file.set(null);
    this.url.set(null);
    this.image = null;
    this.loaded = null;
  }

  private async load(): Promise<void> {
    try {
      const brand = await firstValueFrom(this.api.brand());
      await this.apply(brand.logo ?? null);
    } catch {
      this.loaded = null;
    }
  }

  private async apply(file: CrmFile | null): Promise<void> {
    this.file.set(file);
    if (!file) {
      this.url.set(null);
      return;
    }
    const link = await firstValueFrom(this.api.fileLink(file.id));
    this.url.set(this.api.absolute(link.path));
  }
}
