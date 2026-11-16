package br.com.familhas.crm.clientflight;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Criar ou editar voo. {@code version} é obrigatório na edição (trava otimista). Horários locais do bilhete.
 */
public record ClientFlightRequest(
        @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9]*$", message = "so letras e numeros") String locator,
        @NotBlank @Size(max = 60) String airline,
        @NotBlank @Size(max = 60) String origin,
        @NotBlank @Size(max = 60) String destination,
        @NotNull LocalDateTime outboundAt,
        @Size(max = 20) String outboundFlightNumber,
        LocalDateTime returnAt,
        @Size(max = 20) String returnFlightNumber,
        @Size(max = 2000) String notes,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull Traveler> travelers,
        Long version) {

    /**
     * Passageiro. {@code id} de um passageiro existente mantém o CPF guardado quando {@code cpf} vem null (a tela só
     * recebe o CPF mascarado). {@code cpf} vazio apaga; preenchido é validado pelos dígitos verificadores.
     */
    public record Traveler(UUID id, @NotBlank @Size(max = 150) String fullName, @Size(max = 14) String cpf,
            @Size(max = 20) @Pattern(regexp = "^[0-9 ()+-]*$", message = "telefone invalido") String phone) {
    }
}
