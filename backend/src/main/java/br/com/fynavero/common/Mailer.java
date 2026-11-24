package br.com.fynavero.common;

import br.com.fynavero.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean hostSet;

    /** SPRING_MAIL_HOST vazio (comum ao criar o serviço na hospedagem antes de ter o e-mail) conta como "sem servidor". */
    public Mailer(ObjectProvider<JavaMailSender> sender, AppProperties props,
                  @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.props = props;
        this.hostSet = host != null && !host.isBlank();
    }

    public boolean configured() {
        return hostSet && sender.getIfAvailable() != null;
    }

    /** Envia e devolve true; falhas de envio ficam no log e devolvem false (não derrubam a tarefa). */
    public boolean send(String to, String subject, String text, String html) {
        JavaMailSender s = hostSet ? sender.getIfAvailable() : null;
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
