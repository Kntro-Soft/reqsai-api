package com.kntro.reqsai.iam.application.port;

import com.kntro.reqsai.iam.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link User} aggregate (global {@code public.users} registry).
 * Implemented by an adapter in {@code infrastructure}; the application layer depends only on this.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByAccountId(UUID accountId);
}
