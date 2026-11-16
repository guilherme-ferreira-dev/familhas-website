package br.com.familhas.crm.clientflight;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Passageiro de um voo (V19, voo_travelers). CPF só em dígitos; nunca sai inteiro da API. */
@Entity
@Table(name = "voo_travelers")
@Getter
@Setter
@NoArgsConstructor
public class ClientFlightTravelerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private ClientFlightEntity flight;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "name_search", nullable = false, length = 150)
    private String nameSearch;

    @Column(length = 11)
    private String cpf;

    @Column(length = 20)
    private String phone;
}
