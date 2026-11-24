package br.com.fynavero.common;

import br.com.fynavero.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Envio de e-mail (texto + HTML). Sem servidor configurado (spring.mail.host), o conteúdo vai para o log
 * do back-end, o que mantém links mágicos e avisos utilizáveis em desenvolvimento.
 */
@Component
public class Mailer {
    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties props;

    public Mailer(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    public boolean configured() {
        return sender.getIfAvailable() != null;
    }

    /** Envia e devolve true; falhas de envio ficam no log e devolvem false (não derrubam a tarefa). */
    public boolean send(String to, String subject, String text, String html) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            log.info("E-mail (sem servidor de e-mail configurado) para {} — {}\n{}", to, subject, text);
            return true;
        }
        try {
            MimeMessage m = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, html != null, StandardCharsets.UTF_8.name());
            h.setFrom(props.mailFrom());
            h.setTo(to);
            h.setSubject(subject);
            if (html != null) h.setText(text, html);
            else h.setText(text);
            s.send(m);
            return true;
        } catch (MessagingException | MailException e) {
            log.warn("Falha ao enviar e-mail para {}: {}", to, e.getMessage());
            return false;
        }
    }
}
