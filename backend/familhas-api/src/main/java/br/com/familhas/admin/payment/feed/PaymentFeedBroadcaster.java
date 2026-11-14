package br.com.familhas.admin.payment.feed;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conexões SSE abertas da planilha de pagamentos (ADR 0011), nesta instância da API.
 *
 * - Cada conexão dura no máximo {@link #CONNECTION_TTL}: o Admin reconecta com um token novo, então um token
 *   revogado ou expirado não segue recebendo avisos por muito tempo.
 * - Um comentário a cada 25 s mantém a conexão viva em proxies e detecta quem caiu.
 * - Limite de {@link #MAX_CONNECTIONS} conexões simultâneas: acima disso, 503.
 */
@Component
public class PaymentFeedBroadcaster {

    static final Duration CONNECTION_TTL = Duration.ofMinutes(5);
    static final int MAX_CONNECTIONS = 50;

    private static final Logger log = LoggerFactory.getLogger(PaymentFeedBroadcaster.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        if (emitters.size() >= MAX_CONNECTIONS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many open payment streams");
        }
        SseEmitter emitter = new SseEmitter(CONNECTION_TTL.toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of(), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void broadcast(PaymentFeedEvent event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name("payment").id(event.paymentId().toString())
                    .data(event, MediaType.APPLICATION_JSON));
        }
        log.debug("Aviso de pagamento enviado: paymentId={} eventType={} connections={}", event.paymentId(),
                event.eventType(), emitters.size());
    }

    @Scheduled(fixedDelayString = "PT25S", initialDelayString = "PT25S")
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    int connections() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // Admin fechou a aba ou a conexão caiu: sai da lista; o navegador reconecta se ainda estiver aberto.
            emitters.remove(emitter);
        }
    }
}
