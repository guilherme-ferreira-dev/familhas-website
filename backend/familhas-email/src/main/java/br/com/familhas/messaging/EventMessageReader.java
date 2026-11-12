package br.com.familhas.messaging;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Converte o corpo da mensagem no record do evento e valida com Bean Validation.
 * Campos desconhecidos são ignorados (mudança compatível do contrato). Qualquer problema vira
 * {@link InvalidEventException}, com os nomes dos campos e sem os valores (LGPD).
 */
@Component
public class EventMessageReader {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    private final Validator validator;

    public EventMessageReader(Validator validator) {
        this.validator = validator;
    }

    public <T extends ConsumedEvent> T read(Message message, Class<T> type) {
        T event;
        try {
            event = mapper.readValue(message.getBody(), type);
        } catch (IOException ex) {
            // A mensagem do Jackson pode trazer trechos do payload: não propagar.
            throw new InvalidEventException("JSON invalido para " + type.getSimpleName() + " ("
                    + ex.getClass().getSimpleName() + ")");
        }
        if (event == null) {
            throw new InvalidEventException("Corpo vazio para " + type.getSimpleName());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                    .map(v -> v.getPropertyPath().toString())
                    .sorted()
                    .distinct()
                    .collect(Collectors.joining(","));
            throw new InvalidEventException("Campos invalidos em " + type.getSimpleName() + ": " + fields);
        }
        return event;
    }
}
