package br.com.familhas.crm.flightsearch;

import java.util.List;
import java.util.UUID;

public record FlightSearchGroupResponse(UUID id, String name, List<String> destinations) {

    static FlightSearchGroupResponse of(FlightSearchGroupEntity entity) {
        return new FlightSearchGroupResponse(entity.getId(), entity.getName(), entity.destinationList());
    }
}
