package br.com.familhas.crm.flightsearch;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Grupo de destinos definido pela equipe (V20). Até 7 códigos IATA, separados por vírgula. */
@Entity
@Table(name = "flight_search_groups")
@Getter
@Setter
@NoArgsConstructor
public class FlightSearchGroupEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 40)
    private String destinations;

    public List<String> destinationList() {
        return Arrays.asList(destinations.split(","));
    }
}
