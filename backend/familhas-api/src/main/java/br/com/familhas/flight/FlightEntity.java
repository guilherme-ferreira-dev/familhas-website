package br.com.familhas.flight;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Column(nullable = false)
    private LocalDateTime boardingDate;

    @Column(nullable = false)
    private LocalDateTime returnDate;

    @Column(nullable = false, length = 120)
    private String boardingAirport;

    @Column(nullable = false, length = 120)
    private String returnAirport;
}
