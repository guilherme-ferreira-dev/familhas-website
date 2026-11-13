package br.com.familhas.admin.payment.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

class PaymentFeedListenerTests {

    private final PaymentFeedBroadcaster broadcaster = mock(PaymentFeedBroadcaster.class);
    private final PaymentFeedListener listener = new PaymentFeedListener(broadcaster, new ObjectMapper());

    @Test
    void readsTheFase6ContractExamples() throws Exception {
        for (String example : new String[] { "payment-confirmed.v1.json", "payment-failed.v1.json" }) {
            byte[] body = Files.readAllBytes(Path.of("../../docs/events/examples", example));
            PaymentFeedEvent event = listener.toEvent(message(body, "PaymentX"));
            assertThat(event.paymentId()).isNotNull();
            assertThat(event.bookingId()).isNotNull();
        }
    }

    @Test
    void broadcastsOnlyIdsAndTheEventType() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","paymentId":"%s","bookingId":"%s","email":"ana@example.com",
                 "occurredAt":"2027-01-10T15:00:00Z","schemaVersion":1}
                """.formatted(UUID.randomUUID(), paymentId, bookingId);

        listener.onMessage(message(json.getBytes(StandardCharsets.UTF_8), "PaymentConfirmed"));

        ArgumentCaptor<PaymentFeedEvent> event = ArgumentCaptor.forClass(PaymentFeedEvent.class);
        verify(broadcaster).broadcast(event.capture());
        assertThat(event.getValue()).isEqualTo(new PaymentFeedEvent("PaymentConfirmed", paymentId, bookingId,
                Instant.parse("2027-01-10T15:00:00Z")));
    }

    @Test
    void invalidMessagesAreDroppedWithoutThrowing() {
        listener.onMessage(message("not json".getBytes(StandardCharsets.UTF_8), "PaymentConfirmed"));
        listener.onMessage(message("{\"paymentId\":\"x\"}".getBytes(StandardCharsets.UTF_8), "PaymentFailed"));

        verify(broadcaster, never()).broadcast(any());
    }

    private static Message message(byte[] body, String type) {
        MessageProperties properties = new MessageProperties();
        properties.setType(type);
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(body, properties);
    }
}
