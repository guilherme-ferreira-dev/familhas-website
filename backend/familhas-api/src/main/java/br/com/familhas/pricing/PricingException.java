package br.com.familhas.pricing;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Combinação que não pode ser precificada (ex.: moedas diferentes). Vira 422 em Problem Details. */
public class PricingException extends ResponseStatusException {

    public PricingException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
