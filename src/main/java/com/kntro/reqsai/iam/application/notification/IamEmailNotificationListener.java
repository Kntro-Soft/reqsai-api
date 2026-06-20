package com.kntro.reqsai.iam.application.notification;

import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.event.EmailVerificationRequestedEvent;
import com.kntro.reqsai.iam.domain.event.PasswordResetRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends transactional emails in response to IAM domain events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class IamEmailNotificationListener {

    private final AccountRepository accounts;
    private final UserRepository users;
    private final EmailNotificationPort emailNotification;

    @ApplicationModuleListener
    void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        accounts.findById(event.accountId()).ifPresent(account ->
                users.findByAccountId(account.getId()).ifPresent(user -> {
                    emailNotification.sendVerificationEmail(
                            account.getEmail().value(), user.getFirstName(), event.rawToken());
                    log.info("Verification email sent for account {}", event.accountId());
                }));
    }

    @ApplicationModuleListener
    void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        accounts.findById(event.accountId()).ifPresent(account ->
                users.findByAccountId(account.getId()).ifPresent(user -> {
                    emailNotification.sendPasswordResetEmail(
                            account.getEmail().value(), user.getFirstName(), event.rawToken());
                    log.info("Password reset email sent for account {}", event.accountId());
                }));
    }
}
