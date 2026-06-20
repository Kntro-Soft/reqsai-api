package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.RegisterAccountCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.iam.domain.port.out.EmailVerificationRepositoryPort;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Registers a new account and its user profile (both in the global {@code public} schema).
 * <p>
 * Flow: normalize/validate email → reject if already taken → hash the password → persist the
 * {@code PENDING_VERIFICATION} {@link Account} (raises {@code AccountCreatedEvent}) → persist the linked
 * {@link User} → issue and persist an email-verification token → send the verification email.
 * Transactional so account, profile, and verification token land together (or not at all).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegisterAccountCommandHandler {

    private final AccountRepository accounts;
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final EmailVerificationRepositoryPort emailVerifications;
    private final EmailNotificationPort emailNotification;

    @Transactional
    public User handle(RegisterAccountCommand command) {
        Email email = Email.of(command.email());

        if (accounts.existsByEmail(email)) {
            throw IamExceptions.accountAlreadyExists(email.value());
        }

        Account account = Account.register(email, passwordHasher.hash(command.password()));
        accounts.save(account);

        User user = users.save(new User(account.getId(), command.firstName(), command.lastName()));

        String rawToken = UUID.randomUUID().toString().replace("-", "");
        EmailVerification verification = EmailVerification.issue(account.getId(), rawToken, Instant.now().plus(24, ChronoUnit.HOURS));
        emailVerifications.save(verification);
        emailNotification.sendVerificationEmail(email.value(), command.firstName(), rawToken);

        log.info("Registered account {} (user {})", account.getId(), user.getId());
        return user;
    }
}
