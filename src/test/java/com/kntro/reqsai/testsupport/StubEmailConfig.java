package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * No-op stand-in for the email notification port used in integration tests.
 * Prevents real SMTP connections and keeps tests self-contained.
 */
@TestConfiguration
public class StubEmailConfig {

    @Bean
    @Primary
    public EmailNotificationPort stubEmailNotificationPort() {
        return (toEmail, firstName, rawToken) -> {
            // no-op — email sending is intentionally skipped in tests
        };
    }
}
