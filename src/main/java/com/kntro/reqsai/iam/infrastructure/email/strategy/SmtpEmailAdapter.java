package com.kntro.reqsai.iam.infrastructure.email.strategy;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.infrastructure.exception.IamInfrastructureExceptions;
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
        String link = appUrl + "/auth/verify-email?token=" + rawToken;
        send(toEmail, "Verifica tu correo — Reqs-AI",
                "<p>Hola " + firstName + ",</p><p><a href=\"" + link + "\">Verificar correo</a></p>",
                "verification email");
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        String link = appUrl + "/auth/reset-password?token=" + rawToken;
        send(toEmail, "Restablece tu contraseña — Reqs-AI",
                "<p>Hola " + firstName + ",</p><p><a href=\"" + link + "\">Restablecer contraseña</a></p>" +
                "<p>Este enlace expira en 1 hora. Si no solicitaste este cambio, ignora este mensaje.</p>",
                "password reset email");
    }

    @Override
    public void sendInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                                    String invitedByName, String rawToken) {
        String link = appUrl + "/invitations/accept?token=" + rawToken;
        String inviter = invitedByName != null && !invitedByName.isBlank()
                ? invitedByName + " te ha invitado"
                : "Te han invitado";
        send(toEmail, "Te invitaron a " + organizationName + " — Reqs-AI",
                "<p>Hola " + displayName + ",</p>" +
                "<p>" + inviter + " a unirte a <strong>" + organizationName + "</strong> como " + role + ".</p>" +
                "<p><a href=\"" + link + "\">Aceptar invitación</a></p>",
                "invitation email");
    }

    @Override
    public void sendProjectInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                                           String projectName, String projectRoleName, String invitedByName,
                                           String rawToken) {
        String link = appUrl + "/invitations/accept?token=" + rawToken;
        String inviter = invitedByName != null && !invitedByName.isBlank()
                ? invitedByName + " te ha invitado"
                : "Te han invitado";
        send(toEmail, "Te invitaron a " + organizationName + " — Reqs-AI",
                "<p>Hola " + displayName + ",</p>" +
                "<p>" + inviter + " a unirte a <strong>" + organizationName + "</strong> como " + role + ".</p>" +
                "<p>Al aceptar quedarás asignado al proyecto <strong>" + projectName + "</strong> con el rol "
                + projectRoleName + ".</p>" +
                "<p><a href=\"" + link + "\">Aceptar invitación</a></p>",
                "project invitation email");
    }

    @Override
    public void sendProjectAssignmentEmail(String toEmail, String displayName, String projectName,
                                           String projectRoleName, String projectId) {
        String link = appUrl + "/projects/" + projectId;
        send(toEmail, "Te agregaron al proyecto " + projectName + " — Reqs-AI",
                "<p>Hola " + displayName + ",</p>" +
                "<p>Te agregaron al proyecto <strong>" + projectName + "</strong> con el rol "
                + projectRoleName + ".</p>" +
                "<p><a href=\"" + link + "\">Ir al proyecto</a></p>",
                "project assignment email");
    }

    private void send(String toEmail, String subject, String htmlBody, String emailType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw IamInfrastructureExceptions.emailDeliveryFailed(providerName, emailType, e);
        }
    }
}
