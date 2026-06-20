package com.kntro.reqsai.iam.infrastructure.email.config;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.infrastructure.email.EmailRouter;
import com.kntro.reqsai.iam.infrastructure.email.strategy.SmtpEmailAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Wires the active {@link EmailNotificationPort} based on the {@code reqsai.email.provider}
 * property. All SMTP-based providers (Mailpit, Mailtrap, Gmail) share {@link SmtpEmailAdapter};
 * the provider name is passed for error-message identification only.
 */
@Configuration
class EmailConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmailNotificationPort.class)
    EmailNotificationPort emailRouter(
            JavaMailSender mailSender,
            @Value("${reqsai.email.provider:mailpit}") String provider,
            @Value("${reqsai.url:http://localhost:8080}") String appUrl,
            @Value("${reqsai.email.from:noreply@reqsai.com}") String fromEmail) {
        return new EmailRouter(
                provider,
                new SmtpEmailAdapter(mailSender, appUrl, fromEmail, "Mailpit"),
                new SmtpEmailAdapter(mailSender, appUrl, fromEmail, "Mailtrap"),
                new SmtpEmailAdapter(mailSender, appUrl, fromEmail, "Gmail")
        );
    }
}
