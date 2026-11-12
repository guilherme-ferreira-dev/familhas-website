package br.com.familhas.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Fuso de negócio da Familhas: "hoje" para validar datas de viagem é o dia em São Paulo, não em UTC. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private BusinessTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
