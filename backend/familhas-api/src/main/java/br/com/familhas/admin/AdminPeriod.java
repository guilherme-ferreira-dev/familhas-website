package br.com.familhas.admin;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.common.BusinessTime;

/**
 * Período dos filtros do Admin: dias no fuso de São Paulo, ambos inclusivos. Vira o intervalo
 * [início do dia from, início do dia seguinte a to) em Instant. Qualquer ponta pode faltar.
 */
public record AdminPeriod(Instant start, Instant endExclusive) {

    public static AdminPeriod of(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }
        return new AdminPeriod(
                from == null ? null : from.atStartOfDay(BusinessTime.ZONE).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant());
    }

    public boolean isUnbounded() {
        return start == null && endExclusive == null;
    }
}
