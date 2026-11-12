package br.com.familhas.onboarding;

import org.springframework.stereotype.Service;

import br.com.familhas.email.EmailSender;

/** Monta e envia o e-mail de boas-vindas do UserRegisteredEvent. */
@Service
public class WelcomeEmailService {

    static final String SUBJECT = "Boas-vindas à Familhas";

    private final EmailSender emailSender;

    public WelcomeEmailService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void send(UserRegisteredEvent event) {
        emailSender.sendText(event.email(), SUBJECT, body(event.firstName()));
    }

    static String body(String firstName) {
        String greeting = firstName == null || firstName.isBlank() ? "Olá!" : "Olá, " + firstName.strip() + "!";
        return greeting + """


                Sua conta na Familhas está pronta. A partir de agora você pode buscar pacotes, \
                acompanhar suas viagens e falar com a nossa equipe sempre que precisar.

                Se não foi você quem criou esta conta, é só ignorar este e-mail.

                Boas viagens,
                Equipe Familhas
                """;
    }
}
