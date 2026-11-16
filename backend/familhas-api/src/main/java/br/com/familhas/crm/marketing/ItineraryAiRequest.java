package br.com.familhas.crm.marketing;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Roteiro por IA: cidade e quantidade de dias, ou o PDF do orçamento. */
public record ItineraryAiRequest(@Size(max = 120) String city, @Min(1) @Max(30) Integer days, UUID fileId) {
}
