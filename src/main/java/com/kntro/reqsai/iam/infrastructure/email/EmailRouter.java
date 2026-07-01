package com.kntro.reqsai.iam.infrastructure.email;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.infrastructure.email.strategy.SmtpEmailAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * The single {@link EmailNotificationPort} registered in the application context. Selects the email
 * provider at runtime based on {@code reqsai.email.provider} (default: {@code mailpit}).
 *
 * <ul>
 *   <li>{@code mailpit}  — local SMTP catch-all (Docker, port 1025)
 *   <li>{@code mailtrap} — mailtrap SMTP sandbox (staging, no real sending)
 *   <li>{@code gmail}    — Gmail SMTP with App Password (set MAIL_USERNAME + MAIL_PASSWORD)
 * </ul>
 *
 * Not a {@code @Component} — instantiated by {@code EmailConfiguration} with
 * {@code @ConditionalOnMissingBean} so tests can replace it with a stub.
 */
@Slf4j
public class EmailRouter implements EmailNotificationPort {

    private final String provider;
    private final SmtpEmailAdapter mailpit;
    private final SmtpEmailAdapter mailtrap;
    private final SmtpEmailAdapter gmail;

    public EmailRouter(String provider, SmtpEmailAdapter mailpit, SmtpEmailAdapter mailtrap, SmtpEmailAdapter gmail) {
        this.provider = provider;
        this.mailpit  = mailpit;
        this.mailtrap = mailtrap;
        this.gmail    = gmail;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String firstName, String rawToken) {
        log.debug("Routing verification email to provider '{}' for {}", provider, toEmail);
        resolve().sendVerificationEmail(toEmail, firstName, rawToken);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        log.debug("Routing password reset email to provider '{}' for {}", provider, toEmail);
        resolve().sendPasswordResetEmail(toEmail, firstName, rawToken);
    }

    @Override
    public void sendInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                                    String invitedByName, String rawToken) {
        log.debug("Routing invitation email to provider '{}' for {}", provider, toEmail);
        resolve().sendInvitationEmail(toEmail, displayName, organizationName, role, invitedByName, rawToken);
    }

    private SmtpEmailAdapter resolve() {
        return switch (provider) {
            case "mailtrap" -> mailtrap;
            case "gmail"    -> gmail;
            default         -> mailpit;
        };
    }
}
