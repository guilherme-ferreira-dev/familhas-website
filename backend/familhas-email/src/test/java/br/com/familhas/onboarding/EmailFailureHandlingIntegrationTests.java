package br.com.familhas.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.familhas.email.EmailSender;
import br.com.familhas.email.PermanentEmailException;
import br.com.familhas.messaging.MessagingTopology;
import br.com.familhas.messaging.ProcessedEventRepository;
import br.com.familhas.messaging.TransientProcessingException;
import br.com.familhas.support.MessagingIntegrationTestSupport;

/**
 * Retry e DLQ com falhas simuladas no envio. Retry configurado no suporte: 3 tentativas, backoff de 100 ms a 200 ms.
 */
class EmailFailureHandlingIntegrationTests extends MessagingIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @MockitoBean private EmailSender emailSender;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @Test
    void transientFailureIsRetriedWithBackoffAndThenSucceeds() {
        doThrow(new TransientProcessingException("smtp fora"))
                .doThrow(new TransientProcessingException("smtp fora"))
                .doNothing()
                .when(emailSender).sendText(anyString(), anyString(), anyString());
        UUID eventId = UUID.randomUUID();

        publish(eventId, userRegisteredJson(eventId, uniqueEmail("retry"), "Rita"));

        await().atMost(TIMEOUT).until(() -> processedEventRepository.exists(eventId, UserRegisteredListener.CONSUMER));
        verify(emailSender, times(3)).sendText(anyString(), anyString(), anyString());
        assertThat(rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, 500)).isNull();
    }

    @Test
    void transientFailureThatNeverRecoversEndsInDeadLetterQueue() {
        doThrow(new TransientProcessingException("smtp fora"))
                .when(emailSender).sendText(anyString(), anyString(), anyString());
        UUID eventId = UUID.randomUUID();

        publish(eventId, userRegisteredJson(eventId, uniqueEmail("esgotado"), "Eva"));

        Message dead = rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        verify(emailSender, times(3)).sendText(anyString(), anyString(), anyString());
        assertThat(processedEventRepository.exists(eventId, UserRegisteredListener.CONSUMER))
                .as("rollback libera o eventId para reprocessar a partir da DLQ").isFalse();
    }

    @Test
    void permanentFailureGoesStraightToDeadLetterQueue() {
        doThrow(new PermanentEmailException("SMTP rejeitou o envio (codigo 550)"))
                .when(emailSender).sendText(anyString(), anyString(), anyString());
        UUID eventId = UUID.randomUUID();

        publish(eventId, userRegisteredJson(eventId, uniqueEmail("perm"), "Paula"));

        Message dead = rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        verify(emailSender, times(1)).sendText(anyString(), anyString(), anyString());
        assertThat(processedEventRepository.exists(eventId, UserRegisteredListener.CONSUMER)).isFalse();
    }

    @Test
    void eventFromDeadLetterQueueCanBeReprocessedOnceTheCauseIsFixed() {
        doThrow(new PermanentEmailException("SMTP rejeitou o envio (codigo 550)"))
                .doNothing()
                .when(emailSender).sendText(anyString(), anyString(), anyString());
        UUID eventId = UUID.randomUUID();
        String json = userRegisteredJson(eventId, uniqueEmail("reproc"), "Rui");

        publish(eventId, json);
        assertThat(rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, TIMEOUT.toMillis())).isNotNull();

        publish(eventId, json);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.exists(eventId, UserRegisteredListener.CONSUMER));
        assertThat(mockingDetails(emailSender).getInvocations()).hasSize(2);
    }
}
