package br.com.familhas.messaging;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serializa eventos para o corpo da mensagem. Usa um ObjectMapper próprio, e não o da camada web,
 * para que uma customização da API REST nunca mude o contrato dos eventos sem querer.
 */
@Component
public class EventSerializer {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public String toJson(IntegrationEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // A mensagem do Jackson pode citar valores do evento; não propagar dados pessoais.
            throw new IllegalStateException("Falha ao serializar o evento " + event.type().name()
                    + " eventId=" + event.eventId());
        }
    }
}
