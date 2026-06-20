package com.kntro.reqsai.iam.application.port;

import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.shared.domain.valueobjects.Email;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link Account} aggregate (global {@code public.accounts} registry).
 * Implemented by an adapter in {@code infrastructure}; the application layer depends only on this.
 */
public interface AccountRepository {

    Account save(Account account);

    boolean existsByEmail(Email email);

    Optional<Account> findByEmail(Email email);

    Optional<Account> findById(UUID id);

    Optional<Account> findByPasswordResetToken(String tokenHash);
}
