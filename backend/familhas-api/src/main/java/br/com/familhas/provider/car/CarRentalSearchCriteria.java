package br.com.familhas.provider.car;

import java.time.LocalDate;
import java.util.UUID;

/** passengers filtra pelo número de assentos do carro. */
public record CarRentalSearchCriteria(UUID cityId, LocalDate pickUpDate, LocalDate dropOffDate, int passengers) {
}
