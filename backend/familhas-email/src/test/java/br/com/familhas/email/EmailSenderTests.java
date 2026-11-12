package br.com.familhas.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.util.Map;
import java.util.Properties;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import br.com.familhas.messaging.TransientProcessingException;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailSenderTests {

    private static final String TO = "ana.cliente@exemplo.com";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private EmailSender sender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        sender = new EmailSender(mailSender, new EmailProperties("Familhas <nao-responda@familhas.com.br>", null));
    }

    @Test
    void sendsUtf8TextMessageFromConfiguredSender() throws Exception {
        sender.sendText(TO, "Boas-vindas à Familhas", "Olá, Ana!");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("nao-responda@familhas.com.br");
        assertThat(((InternetAddress) sent.getFrom()[0]).getPersonal()).isEqualTo("Familhas");
        assertThat(sent.getRecipients(RecipientType.TO)[0].toString()).isEqualTo(TO);
        assertThat(sent.getSubject()).isEqualTo("Boas-vindas à Familhas");
        assertThat(sent.getContent()).isEqualTo("Olá, Ana!");
    }

    @Test
    void connectionFailureIsTransient() {
        doThrow(new MailSendException("Mail server connection failed",
                new MessagingException("Couldn't connect to host", new ConnectException("Connection refused"))))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendText(TO, "s", "b"))
                .isInstanceOf(TransientProcessingException.class)
                .hasMessageContaining("ConnectException")
                .hasNoCause();
    }

    @Test
    void smtp5xxRecipientRejectionIsPermanentAndHidesAddress() throws Exception {
        SMTPAddressFailedException rejected = new SMTPAddressFailedException(new InternetAddress(TO), "RCPT TO",
                550, "550 5.1.1 <" + TO + ">: Recipient address rejected");
        doThrow(new MailSendException(Map.of(new Object(), new SendFailedException("Invalid Addresses", rejected))))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendText(TO, "s", "b"))
                .isInstanceOf(PermanentEmailException.class)
                .hasMessageContaining("550")
                .hasMessageNotContaining(TO)
                .hasNoCause();
    }

    @Test
    void smtp4xxIsTransient() {
        SMTPSendFailedException busy = new SMTPSendFailedException("DATA", 451, "451 4.3.0 try again later", null,
                null, null, null);
        doThrow(new MailSendException("falhou", busy)).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendText(TO, "s", "b"))
                .isInstanceOf(TransientProcessingException.class)
                .hasMessageContaining("451");
    }

    @Test
    void authenticationFailureIsPermanent() {
        doThrow(new MailAuthenticationException("535 bad credentials")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendText(TO, "s", "b")).isInstanceOf(PermanentEmailException.class);
    }

    @Test
    void malformedRecipientIsPermanent() {
        assertThatThrownBy(() -> sender.sendText("nao é e-mail@@", "s", "b"))
                .isInstanceOf(PermanentEmailException.class)
                .hasMessageNotContaining("@@");
    }
}
