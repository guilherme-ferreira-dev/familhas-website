import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
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

  protected readonly destination = signal('');
  protected readonly passengers = signal(2);
  protected readonly passengerOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  protected readonly offers = rxResource({
    stream: () => this.api.searchPackages({ size: 6, passengers: 1 }).pipe(map((p) => p.content)),
  });

  protected readonly categories = rxResource({
    stream: () => this.api.categories().pipe(map((p) => p.content)),
  });

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
