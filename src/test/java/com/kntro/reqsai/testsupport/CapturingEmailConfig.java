package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test stand-in for {@link EmailNotificationPort} that captures the raw invitation token per invited
 * email instead of sending mail. Lets invitation integration tests recover the tokenized acceptance
 * link (which the API never returns) so they can exercise the accept/get-by-token endpoints.
 */
@TestConfiguration
public class CapturingEmailConfig {

    /** Exposed as a bean so tests can read the captured tokens. */
    @Bean
    public InvitationTokenCapture invitationTokenCapture() {
        return new InvitationTokenCapture();
    }

    @Bean
    @Primary
    public EmailNotificationPort capturingEmailNotificationPort(InvitationTokenCapture capture) {
        return new EmailNotificationPort() {
            @Override
            public void sendVerificationEmail(String toEmail, String firstName, String rawToken) {}

            @Override
            public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {}

            @Override
            public void sendInvitationEmail(String toEmail, String displayName, String organizationName,
                                            String role, String invitedByName, String rawToken) {
                capture.record(toEmail, rawToken);
            }
        };
    }

    /** Thread-safe map of invited email → last raw invitation token seen. */
    public static final class InvitationTokenCapture {
        private final Map<String, String> tokensByEmail = new ConcurrentHashMap<>();

        void record(String email, String rawToken) {
            tokensByEmail.put(email.toLowerCase(), rawToken);
        }

        public String tokenFor(String email) {
            return tokensByEmail.get(email.toLowerCase());
        }
    }
}
