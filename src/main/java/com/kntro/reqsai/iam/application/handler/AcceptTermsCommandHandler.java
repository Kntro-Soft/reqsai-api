package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.AcceptTermsCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records the user's acceptance of the Terms and Conditions.
 * <p>
 * Flow: load user by userId → load linked account by accountId → call
 * {@link Account#acceptTerms(String, Instant)} → persist. The frontend should
 * immediately request a token refresh ({@code POST /api/auth/refresh}) so the next
 * JWT carries the updated {@code termsVersion} claim, unlocking the onboarding flow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AcceptTermsCommandHandler {

    private final UserRepository users;
    private final AccountRepository accounts;

    @Transactional
    public void handle(AcceptTermsCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        Account account = accounts.findById(user.getAccountId())
                .orElseThrow(() -> IamExceptions.userNotFound(user.getAccountId()));

        account.acceptTerms(command.termsVersion(), Instant.now());
        accounts.save(account);

        log.info("User {} accepted terms version '{}'", command.userId(), command.termsVersion());
    }
}
