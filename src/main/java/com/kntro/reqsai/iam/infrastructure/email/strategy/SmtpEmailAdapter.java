package com.kntro.reqsai.iam.infrastructure.email.strategy;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.infrastructure.email.template.EmailContent;
import com.kntro.reqsai.iam.infrastructure.email.template.EmailTemplateRenderer;
import com.kntro.reqsai.iam.infrastructure.exception.IamInfrastructureExceptions;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.List;

/**
 * SMTP-based implementation of {@link EmailNotificationPort} using Spring's {@link JavaMailSender}.
 * A single instance of this class is wired per active email provider (Mailpit, Mailtrap, Gmail)
 * via {@code EmailConfiguration}; the {@code providerName} field identifies the provider in
 * exception messages.
 * <p>
 * All three supported SMTP providers share the same JavaMail protocol. HTTP-based providers
 * (e.g. Resend, SendGrid) require a separate adapter that uses {@code RestClient} with an API key
 * instead of {@link JavaMailSender}.
 * <p>
 * Every message is sent as {@code multipart/alternative} — an HTML body rendered via
 * {@link EmailTemplateRenderer} plus a plain-text fallback generated from the same
 * {@link EmailContent}, so clients that can't (or won't) render HTML still get a readable message.
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
        EmailContent content = new EmailContent(
                "Verifica tu correo para activar tu cuenta en ReqsAI",
                "Verifica tu correo",
                List.of(
                        "Hola " + firstName + ",",
                        "Confirma tu dirección de correo para activar tu cuenta y empezar a usar ReqsAI."
                ),
                "Verificar correo", link,
                "Este enlace expira en 24 horas. Si no creaste una cuenta en ReqsAI, ignora este mensaje."
        );
        send(toEmail, "Verifica tu correo — ReqsAI", content, "verification email");
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        String link = appUrl + "/auth/reset-password?token=" + rawToken;
        EmailContent content = new EmailContent(
                "Restablece tu contraseña de ReqsAI",
                "Restablece tu contraseña",
                List.of(
                        "Hola " + firstName + ",",
                        "Recibimos una solicitud para restablecer tu contraseña de ReqsAI."
                ),
                "Restablecer contraseña", link,
                "Este enlace expira en 1 hora. Si no solicitaste este cambio, ignora este mensaje — tu " +
                        "contraseña actual sigue siendo válida."
        );
        send(toEmail, "Restablece tu contraseña — ReqsAI", content, "password reset email");
    }

    @Override
    public void sendInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                                    String invitedByName, String rawToken) {
        String link = appUrl + "/invitations/accept?token=" + rawToken;
        String inviter = invitedByName != null && !invitedByName.isBlank()
                ? invitedByName + " te ha invitado"
                : "Te han invitado";
        EmailContent content = new EmailContent(
                inviter + " a unirte a " + organizationName + " en ReqsAI",
                "Te invitaron a colaborar",
                List.of(
                        "Hola " + displayName + ",",
                        inviter + " a unirte a **" + organizationName + "** como **" + role + "**."
                ),
                "Aceptar invitación", link,
                "Si no esperabas esta invitación, puedes ignorar este mensaje con seguridad."
        );
        send(toEmail, "Te invitaron a " + organizationName + " — ReqsAI", content, "invitation email");
    }

    @Override
    public void sendProjectInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                                           String projectName, String projectRoleName, String invitedByName,
                                           String rawToken) {
        String link = appUrl + "/invitations/accept?token=" + rawToken;
        String inviter = invitedByName != null && !invitedByName.isBlank()
                ? invitedByName + " te ha invitado"
                : "Te han invitado";
        EmailContent content = new EmailContent(
                inviter + " a unirte a " + organizationName + " y al proyecto " + projectName + " en ReqsAI",
                "Te invitaron a un proyecto",
                List.of(
                        "Hola " + displayName + ",",
                        inviter + " a unirte a **" + organizationName + "** como **" + role + "**.",
                        "Al aceptar quedarás asignado al proyecto **" + projectName + "** con el rol **"
                                + projectRoleName + "**."
                ),
                "Aceptar invitación", link,
                "Si no esperabas esta invitación, puedes ignorar este mensaje con seguridad."
        );
        send(toEmail, "Te invitaron a " + organizationName + " y al proyecto " + projectName + " — ReqsAI",
                content, "project invitation email");
    }

    @Override
    public void sendProjectAssignmentEmail(String toEmail, String displayName, String projectName,
                                           String projectRoleName, String projectId) {
        String link = appUrl + "/projects/" + projectId;
        EmailContent content = new EmailContent(
                "Te agregaron al proyecto " + projectName + " en ReqsAI",
                "Te agregaron a un proyecto",
                List.of(
                        "Hola " + displayName + ",",
                        "Te agregaron al proyecto **" + projectName + "** con el rol **" + projectRoleName + "**."
                ),
                "Ir al proyecto", link,
                null
        );
        send(toEmail, "Te agregaron al proyecto " + projectName + " — ReqsAI", content, "project assignment email");
    }

    private void send(String toEmail, String subject, EmailContent content, String emailType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true is required for setText(text, html) below to attach an alternative
            // plain-text part; Spring nests it as mixed > alternative > {text/plain, text/html}
            // since there are no attachments/inline resources, which every mail client unwraps
            // transparently to render the HTML part.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(EmailTemplateRenderer.plainText(content), EmailTemplateRenderer.html(content));
            mailSender.send(message);
        } catch (Exception e) {
            throw IamInfrastructureExceptions.emailDeliveryFailed(providerName, emailType, e);
        }
    }
}
