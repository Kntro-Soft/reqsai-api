package com.kntro.reqsai.iam.infrastructure.email.strategy;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@RequiredArgsConstructor
public class GmailEmailAdapter implements EmailNotificationPort {

    private final JavaMailSender mailSender;
    private final String appUrl;
    private final String fromEmail;

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
            throw new IllegalStateException("Failed to send verification email via Gmail", e);
        }
    }
}
