package br.com.familhas.crm.outbound;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.familhas.api.BusinessException;

/**
 * GET de URL externa com a {@link OutboundUrlPolicy}: redirects seguidos à mão (no máximo 3, cada um revalidado),
 * timeouts curtos e corpo limitado. Usado pelos módulos que importam link de orçamento ou imagem (Etapas 3 e 4).
 */
@Component
public class SafeHttpFetcher {

    public static final int MAX_REDIRECTS = 3;
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;

    private final OutboundUrlPolicy policy;
    private final HttpClient client;

    public SafeHttpFetcher(OutboundUrlPolicy policy) {
        this.policy = policy;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public record Fetched(URI finalUri, String contentType, byte[] body) {
    }

    public Fetched get(String url, long maxBytes) {
        URI uri = policy.check(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                close(response);
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty()) {
                    throw unavailable();
                }
                uri = policy.check(uri.resolve(location.get()));
                continue;
            }
            if (status != 200) {
                close(response);
                throw unavailable();
            }
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
                if (bytes.length > maxBytes) {
                    throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "REMOTE_CONTENT_TOO_LARGE",
                            "Conteudo do endereco maior que o permitido.");
                }
                return new Fetched(uri, response.headers().firstValue("Content-Type").orElse(null), bytes);
            } catch (IOException e) {
                throw unavailable();
            }
        }
        throw new OutboundUrlRejectedException("redirects demais");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "VoeFamilhasCRM/1.0").GET().build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // nada a fazer
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "REMOTE_UNAVAILABLE",
                "Nao foi possivel buscar o endereco informado.");
    }
}
