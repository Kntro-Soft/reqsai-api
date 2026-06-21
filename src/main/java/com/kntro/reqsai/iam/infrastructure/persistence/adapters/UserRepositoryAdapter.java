package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.infrastructure.persistence.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link UserRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<User> findByAccountId(UUID accountId) {
        return jpa.findByAccountId(accountId);
    }
}
