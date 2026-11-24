package br.com.fynavero.common;

import br.com.fynavero.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envio de e-mail (texto + HTML), em três modos, escolhidos pelas variáveis de ambiente:
 * <ol>
 *   <li><b>API do Brevo</b> (BREVO_API_KEY): envio por HTTPS, que nenhuma hospedagem bloqueia — inclusive planos
 *       grátis que fecham as portas de SMTP;</li>
 *   <li><b>SMTP</b> (SPRING_MAIL_HOST e companhia): Brevo, Resend, SES ou qualquer servidor;</li>
 *   <li><b>Log</b>: sem nada configurado, o conteúdo vai para o log do back-end (útil em desenvolvimento).</li>
 * </ol>
 */
@Component
public class Mailer {
    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final URI BREVO_API = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final Pattern NAMED_ADDRESS = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+?)\\s*>\\s*$");

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties props;
    private final ObjectMapper json;
    private final boolean hostSet;
    private final String brevoKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Valores vazios (comuns ao criar o serviço na hospedagem antes de ter o e-mail) contam como "não configurado". */
    public Mailer(ObjectProvider<JavaMailSender> sender, AppProperties props, ObjectMapper json,
                  @Value("${spring.mail.host:}") String host, @Value("${BREVO_API_KEY:}") String brevoKey) {
        this.sender = sender;
        this.props = props;
        this.json = json;
        this.hostSet = host != null && !host.isBlank();
        this.brevoKey = brevoKey == null ? "" : brevoKey.strip();
    }

    @PostConstruct
    void announce() {
        if (!brevoKey.isEmpty()) log.info("E-mail: envio pela API do Brevo, remetente {}", props.mailFrom());
        else if (configured()) log.info("E-mail: envio por SMTP, remetente {}", props.mailFrom());
        else log.info("E-mail: nenhum servidor configurado; o conteúdo dos e-mails vai para o log");
    }

    public boolean configured() {
        return !brevoKey.isEmpty() || (hostSet && sender.getIfAvailable() != null);
    }

    /** Envia e devolve true; falhas de envio ficam no log e devolvem false (não derrubam a tarefa). */
    public boolean send(String to, String subject, String text, String html) {
        if (!brevoKey.isEmpty()) return sendWithBrevoApi(to, subject, text, html);
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
            log.warn("Falha ao enviar e-mail para {} por SMTP: {}", to, e.getMessage());
            return false;
        }
    }

    private boolean sendWithBrevoApi(String to, String subject, String text, String html) {
        String[] from = parseAddress(props.mailFrom());
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, String> senderMap = new LinkedHashMap<>();
        if (!from[0].isEmpty()) senderMap.put("name", from[0]);
        senderMap.put("email", from[1]);
        body.put("sender", senderMap);
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("textContent", text);
        if (html != null) body.put("htmlContent", html);
        try {
            HttpRequest req = HttpRequest.newBuilder(BREVO_API)
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", brevoKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) return true;
            String detail = res.body() == null ? "" : res.body();
            log.warn("Falha ao enviar e-mail para {} pela API do Brevo (HTTP {}): {}", to, res.statusCode(),
                    detail.length() > 300 ? detail.substring(0, 300) : detail);
            return false;
        } catch (IOException e) {
            log.warn("Falha ao enviar e-mail para {} pela API do Brevo: {}", to, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** "Fynavero <nao-responda@x.com>" → {"Fynavero", "nao-responda@x.com"}; "a@x.com" → {"", "a@x.com"}. */
    static String[] parseAddress(String raw) {
        String s = raw == null ? "" : raw.strip();
        Matcher m = NAMED_ADDRESS.matcher(s);
        if (m.matches()) return new String[]{m.group(1).replaceAll("^\"|\"$", ""), m.group(2)};
        return new String[]{"", s};
    }
}
