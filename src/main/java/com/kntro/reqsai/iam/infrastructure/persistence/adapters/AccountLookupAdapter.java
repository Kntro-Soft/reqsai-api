package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * IAM-internal implementation of {@link AccountLookupPort}: resolves the account email behind a user
 * id (JWT {@code sub}) by following {@code User → accountId → Account.email}. Kept inside IAM so the
 * consuming module never touches IAM internals.
 */
@Component
@RequiredArgsConstructor
public class AccountLookupAdapter implements AccountLookupPort {

    private final UserRepository users;
    private final AccountRepository accounts;

    @Override
    public Optional<String> findEmailByUserId(UUID userId) {
        return users.findById(userId)
                .flatMap(user -> accounts.findById(user.getAccountId()))
                .map(account -> account.getEmail().value());
    }

    @Override
    public Optional<UUID> findUserIdByAccountId(UUID accountId) {
        return users.findByAccountId(accountId).map(user -> user.getId());
    }

    @Override
    public Optional<UserProfile> findProfileByUserId(UUID userId) {
        return users.findById(userId).flatMap(user -> accounts.findById(user.getAccountId())
                .map(account -> new UserProfile(account.getEmail().value(), user.getFullName())));
    }
}
