package br.com.familhas.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.familhas.email.EmailSender;

class WelcomeEmailServiceTests {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final WelcomeEmailService service = new WelcomeEmailService(emailSender);

    @Test
    void sendsGreetingWithFirstNameToEventEmail() {
        service.send(new UserRegisteredEvent(UUID.randomUUID(), UUID.randomUUID(), "ana@exemplo.com", " Ana ",
                Instant.now(), 1));

        verify(emailSender).sendText(eq("ana@exemplo.com"), eq("Boas-vindas à Familhas"), contains("Olá, Ana!"));
    }

    @Test
    void genericGreetingWhenFirstNameIsMissing() {
        String body = WelcomeEmailService.body(null);

        assertThat(body).startsWith("Olá!\n\nSua conta na Familhas está pronta.");
        assertThat(body).contains("Equipe Familhas");
    }
}
