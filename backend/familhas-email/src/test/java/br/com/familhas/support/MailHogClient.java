package br.com.familhas.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.internet.MimeUtility;

/** Lê o que chegou no MailHog pela API HTTP v2 (a mesma que a interface web usa). */
public class MailHogClient {

    public record ReceivedEmail(String subject, String body) {
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public MailHogClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<ReceivedEmail> messagesTo(String recipient) {
        try {
            String url = baseUrl + "/api/v2/search?kind=to&query="
                    + URLEncoder.encode(recipient, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            List<ReceivedEmail> emails = new ArrayList<>();
            for (JsonNode item : mapper.readTree(response.body()).path("items")) {
                JsonNode content = item.path("Content");
                String subject = MimeUtility.decodeText(content.path("Headers").path("Subject").path(0).asText());
                String encoding = content.path("Headers").path("Content-Transfer-Encoding").path(0).asText("7bit");
                emails.add(new ReceivedEmail(subject, decodeBody(content.path("Body").asText(), encoding)));
            }
            return emails;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static String decodeBody(String raw, String encoding) throws IOException {
        try (var in = MimeUtility.decode(new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)),
                encoding)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (jakarta.mail.MessagingException ex) {
            throw new IOException(ex);
        }
    }
}
