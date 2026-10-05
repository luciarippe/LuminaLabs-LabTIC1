package uy.edu.um.luminalabs.notifications;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uy.edu.um.luminalabs.events.MailRecipient;

import java.util.Locale;

// Arma el HTML del correo y lo envia por SMTP
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String from;
    @Value("${app.mail.reply-to}")
    private String replyTo;
    @Value("${app.base-url}")
    private String baseUrl;

    // Un correo que no se pudo enviar se registra en el log, pero no interrumpe la operacion del usuario
    public void send(MailRecipient recipient, EmailContent content) {
        try {
            Context context = new Context(Locale.forLanguageTag("es-UY"));
            context.setVariable("recipient", recipient);
            context.setVariable("content", content);
            context.setVariable("baseUrl", baseUrl);
            String html = templateEngine.process("mail/notification", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setReplyTo(replyTo);
            helper.setTo(recipient.email());
            helper.setSubject(content.subject());
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email '{}' sent to {}", content.subject(), recipient.email());
        } catch (MailException | MessagingException e) {
            log.warn("Could not send email '{}' to {}: {}", content.subject(), recipient.email(), e.getMessage());
        }
    }

    public String url(String path) {
        return baseUrl + path;
    }
}
