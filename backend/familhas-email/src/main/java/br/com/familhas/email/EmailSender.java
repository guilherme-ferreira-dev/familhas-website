package br.com.familhas.email;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import br.com.familhas.messaging.TransientProcessingException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

/**
 * Envia e-mail de texto pelo SMTP configurado (MailHog no ambiente local) e classifica as falhas:
 * <ul>
 * <li>{@link TransientProcessingException}: conexão recusada, timeout, código SMTP 4xx. O listener tenta de novo.</li>
 * <li>{@link PermanentEmailException}: código SMTP 5xx, endereço inválido, autenticação, mensagem malformada.
 * Vai para a DLQ.</li>
 * </ul>
 * As exceções criadas aqui não encadeiam a causa original: respostas SMTP costumam citar o endereço do
 * destinatário, e a causa acabaria no stack trace do log (LGPD).
 */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public EmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendText(String to, String subject, String text) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
        } catch (MessagingException ex) {
            throw new PermanentEmailException("Nao foi possivel montar o e-mail (" + ex.getClass().getSimpleName()
                    + ")");
        }
        try {
            mailSender.send(message);
        } catch (MailAuthenticationException ex) {
            throw new PermanentEmailException("SMTP recusou a autenticacao");
        } catch (MailException ex) {
            throw classify(ex);
        }
    }

    static RuntimeException classify(MailException ex) {
        Integer code = null;
        boolean invalidAddress = false;
        for (Throwable t : causes(ex)) {
            if (t instanceof SMTPAddressFailedException address) {
                code = address.getReturnCode();
            } else if (t instanceof SMTPSendFailedException send) {
                code = send.getReturnCode();
            } else if (t instanceof AddressException) {
                invalidAddress = true;
            } else if (t instanceof SendFailedException sendFailed && sendFailed.getInvalidAddresses() != null
                    && sendFailed.getInvalidAddresses().length > 0) {
                invalidAddress = true;
            }
            if (code != null) {
                break;
            }
        }
        if (code != null && code >= 500) {
            return new PermanentEmailException("SMTP rejeitou o envio (codigo " + code + ")");
        }
        if (code == null && invalidAddress) {
            return new PermanentEmailException("Endereco de destino invalido");
        }
        String detail = code != null ? "codigo " + code : rootCause(ex).getClass().getSimpleName();
        return new TransientProcessingException("Falha transitoria no SMTP (" + detail + ")");
    }

    /** Percorre getCause, getNextException do Jakarta Mail e as exceções por mensagem do Spring. */
    private static Iterable<Throwable> causes(MailException ex) {
        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> seen = new HashSet<>();
        java.util.List<Throwable> ordered = new java.util.ArrayList<>();
        pending.add(ex);
        if (ex instanceof MailSendException send) {
            pending.addAll(java.util.Arrays.asList(send.getMessageExceptions()));
        }
        while (!pending.isEmpty()) {
            Throwable t = pending.poll();
            if (t == null || !seen.add(t)) {
                continue;
            }
            ordered.add(t);
            if (t.getCause() != null) {
                pending.add(t.getCause());
            }
            if (t instanceof MessagingException messaging && messaging.getNextException() != null) {
                pending.add(messaging.getNextException());
            }
        }
        return ordered;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
