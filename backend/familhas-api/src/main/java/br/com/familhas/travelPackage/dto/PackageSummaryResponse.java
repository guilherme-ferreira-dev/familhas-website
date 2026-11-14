package br.com.familhas.travelPackage.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.familhas.enums.EntityStatus;
import br.com.familhas.travelPackage.TravelPackageEntity;

/**
 * Pacote resumido para cartões da área do cliente (favoritos, avaliações, recomendações e viagens).
 * Preço "a partir de" por pessoa, como na vitrine: a promoção vale quando é menor que o preço.
 */
public record PackageSummaryResponse(
        UUID id,
        String title,
        String imageUrl,
        UUID categoryId,
        String categoryTitle,
        UUID destinationCityId,
        String destinationCityName,
        String destinationCountry,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        BigDecimal pricePerPerson,
        BigDecimal originalPricePerPerson,
        String currency,
        boolean active) {

    /** Lê categoria, destino e voo: carregue-os junto (EntityGraph ou batch fetch) para não gerar N+1. */
    public static PackageSummaryResponse of(TravelPackageEntity travelPackage) {
        var flight = travelPackage.getFlight();
        var destination = travelPackage.getDestination();
        return new PackageSummaryResponse(
                travelPackage.getId(),
                travelPackage.getTitle(),
                travelPackage.getImageUrl(),
                travelPackage.getCategory().getId(),
                travelPackage.getCategory().getTitle(),
                destination.getId(),
                destination.getName(),
                destination.getCountry(),
                flight == null ? null : flight.getBoardingDate(),
                flight == null ? null : flight.getReturnDate(),
                effectivePrice(travelPackage),
                travelPackage.getPrice(),
                travelPackage.getCurrency(),
                travelPackage.getStatus() == EntityStatus.ACTIVE);
    }

    public static boolean onPromotion(TravelPackageEntity travelPackage) {
        return travelPackage.getPricePromotion() != null
                && travelPackage.getPricePromotion().compareTo(travelPackage.getPrice()) < 0;
    }

    private static BigDecimal effectivePrice(TravelPackageEntity travelPackage) {
        return onPromotion(travelPackage) ? travelPackage.getPricePromotion() : travelPackage.getPrice();
    }
}
