package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.RegisterAccountCommand;
import com.kntro.reqsai.iam.application.config.IamTokenProperties;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.EmailVerification;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.shared.domain.support.TokenGenerator;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import com.kntro.reqsai.shared.infrastructure.avatar.AvatarDownloadAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    private static final String AVATAR_URL_TEMPLATE = "https://api.dicebear.com/9.x/glass/svg?seed=%s";

    private final AccountRepository accounts;
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final EmailVerificationRepository emailVerifications;
    private final IamTokenProperties tokenProperties;
    private final AvatarDownloadAdapter avatarDownloadAdapter;

    @Transactional
    public User handle(RegisterAccountCommand command) {
        Email email = Email.of(command.email());

        if (accounts.existsByEmail(email)) {
            throw IamExceptions.accountAlreadyExists(email.value());
        }

        Account account = Account.register(email, passwordHasher.hash(command.password()));
        accounts.save(account);

        User user = users.save(new User(account.getId(), command.firstName(), command.lastName()));

        avatarDownloadAdapter.download(AVATAR_URL_TEMPLATE.formatted(user.getId()))
                .ifPresent(avatar -> user.applyAvatar(avatar.bytes(), avatar.contentType()));

        String rawToken = TokenGenerator.generate(tokenProperties.tokenBytes());
        EmailVerification verification = EmailVerification.issue(
                account.getId(), rawToken, Instant.now().plus(tokenProperties.emailVerificationExpiration()));
        emailVerifications.save(verification);

        log.info("Registered account {} (user {})", account.getId(), user.getId());
        return user;
    }
}
