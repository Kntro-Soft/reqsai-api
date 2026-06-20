package com.kntro.reqsai.iam.infrastructure.email.config;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.infrastructure.email.EmailRouter;
import com.kntro.reqsai.iam.infrastructure.email.strategy.GmailEmailAdapter;
import com.kntro.reqsai.iam.infrastructure.email.strategy.MailpitEmailAdapter;
import com.kntro.reqsai.iam.infrastructure.email.strategy.MailtrapEmailAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

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
                new MailpitEmailAdapter(mailSender, appUrl, fromEmail),
                new MailtrapEmailAdapter(mailSender, appUrl, fromEmail),
                new GmailEmailAdapter(mailSender, appUrl, fromEmail)
        );
    }
}
