import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  numberAttribute,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import type {
  CarRentalOffer,
  Flight,
  Hotel,
  RoomOffer,
  TravelPackage,
} from '../../core/api/api-types';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogApi } from '../../core/catalog/catalog-api';
import { CityDirectory } from '../../core/catalog/city-directory';
import { CheckoutApi } from '../../core/checkout/checkout-api';
import { ApiError, errorMessage } from '../../core/errors/api-error';
import { Seo } from '../../core/seo/seo';
import { WhatsappContextService } from '../../core/whatsapp/whatsapp';
import { LoadError } from '../../shared/load-error';
import { BOARD_LABELS, CAR_LABELS } from '../search/search.page';

interface PackageDetail {
  pkg: TravelPackage;
  flight: Flight;
  stays: { room: RoomOffer; hotel: Hotel; quantity: number }[];
  cars: { car: CarRentalOffer; quantity: number }[];
}

@Component({
  selector: 'app-package-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, LoadError],
  templateUrl: './package-detail.page.html',
  styleUrl: './package-detail.page.scss',
})
export class PackageDetailPage {
  private readonly catalog = inject(CatalogApi);
  private readonly checkout = inject(CheckoutApi);
  private readonly router = inject(Router);
  private readonly whatsapp = inject(WhatsappContextService);
  private readonly seo = inject(Seo);
  protected readonly auth = inject(AuthService);
  protected readonly cities = inject(CityDirectory);
  protected readonly boardLabels = BOARD_LABELS;
  protected readonly carLabels = CAR_LABELS;
  protected readonly passengerOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  /** Rota /pacotes/:id (withComponentInputBinding). */
  readonly id = input.required<string>();
  /** ?passageiros= vindo da busca. */
  readonly passageiros = input(2, { transform: (v: unknown) => clamp(numberAttribute(v, 2)) });

  protected readonly passengers = linkedSignal(() => this.passageiros());
  protected readonly quoting = signal(false);
  protected readonly quoteError = signal<string | null>(null);

  protected readonly detail = rxResource<PackageDetail, string>({
    params: () => this.id(),
    stream: ({ params: id }) =>
      this.catalog.travelPackage(id).pipe(
        switchMap((pkg) => {
          const stays = pkg.components.filter((c) => c.type === 'HOTEL_STAY' && c.roomOfferId);
          const cars = pkg.components.filter((c) => c.type === 'CAR_RENTAL' && c.carRentalOfferId);
          return forkJoin({
            pkg: of(pkg),
            flight: this.catalog.flight(pkg.flightId),
            stays: stays.length
              ? forkJoin(
                  stays.map((c) =>
                    this.catalog
                      .roomOffer(c.roomOfferId!)
                      .pipe(
                        switchMap((room) =>
                          this.catalog
                            .hotel(room.hotelId)
                            .pipe(map((hotel) => ({ room, hotel, quantity: c.quantity ?? 1 }))),
                        ),
                      ),
                  ),
                )
              : of([]),
            cars: cars.length
              ? forkJoin(
                  cars.map((c) =>
                    this.catalog
                      .carRentalOffer(c.carRentalOfferId!)
                      .pipe(map((car) => ({ car, quantity: c.quantity ?? 1 }))),
                  ),
                )
              : of([]),
          });
        }),
      ),
  });

  protected readonly notFound = computed(() => {
    const error = this.detail.error();
    return error instanceof ApiError && error.kind === 'not-found';
  });

  protected readonly pricePerPerson = computed(() => {
    if (!this.detail.hasValue()) return null;
    const { pkg } = this.detail.value();
    return pkg.pricePromotion ?? pkg.price;
  });

  constructor() {
    effect(() => {
      if (this.detail.hasValue()) {
        const { pkg } = this.detail.value();
        this.whatsapp.set({ kind: 'package', id: pkg.id, title: pkg.title });
        const destination = this.cities.name(pkg.destinationCityId);
        this.seo.apply({
          title: `${pkg.title} | Familhas`,
          description: `Pacote ${pkg.title}${destination ? ` para ${destination}` : ''} com voo${
            pkg.components.length ? ', hospedagem e mais' : ''
          }. Veja o que está incluído e o preço por pessoa.`,
          path: `/pacotes/${pkg.id}`,
        });
      }
    });
  }

  protected requestQuote(): void {
    if (!this.auth.isAuthenticated()) {
      this.auth.login(`/pacotes/${encodeURIComponent(this.id())}?passageiros=${this.passengers()}`);
      return;
    }
    this.quoting.set(true);
    this.quoteError.set(null);
    this.checkout
      .createQuote({ travelPackageId: this.id(), passengers: this.passengers() })
      .subscribe({
        next: (quote) => void this.router.navigate(['/cotacoes', quote.id]),
        error: (error: unknown) => {
          this.quoting.set(false);
          if (!(error instanceof ApiError && error.kind === 'unauthorized')) {
            this.quoteError.set(errorMessage(error));
          }
        },
      });
  }
}

function clamp(n: number): number {
  return Math.min(9, Math.max(1, Math.trunc(n) || 1));
}
