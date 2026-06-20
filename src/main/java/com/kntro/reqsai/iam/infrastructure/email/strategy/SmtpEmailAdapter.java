package com.kntro.reqsai.iam.infrastructure.email.strategy;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP-based implementation of {@link EmailNotificationPort} using Spring's {@link JavaMailSender}.
 * A single instance of this class is wired per active email provider (Mailpit, Mailtrap, Gmail)
 * via {@code EmailConfiguration}; the {@code providerName} field identifies the provider in
 * exception messages.
 * <p>
 * All three supported SMTP providers share the same JavaMail protocol. HTTP-based providers
 * (e.g. Resend, SendGrid) require a separate adapter that uses {@code RestClient} with an API key
 * instead of {@link JavaMailSender}.
 */
@RequiredArgsConstructor
public class SmtpEmailAdapter implements EmailNotificationPort {

    private final JavaMailSender mailSender;
    private final String appUrl;
    private final String fromEmail;
    private final String providerName;

    @Override
    public void sendVerificationEmail(String toEmail, String firstName, String rawToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verifica tu correo — Reqs-AI");
            String link = appUrl + "/auth/verify-email?token=" + rawToken;
            helper.setText("<p>Hola " + firstName + ",</p><p><a href=\"" + link + "\">Verificar correo</a></p>", true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send verification email via " + providerName, e);
        }
    }
}
