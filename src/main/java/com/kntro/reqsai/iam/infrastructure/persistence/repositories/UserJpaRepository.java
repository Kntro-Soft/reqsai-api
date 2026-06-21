package com.kntro.reqsai.iam.infrastructure.persistence.repositories;

import com.kntro.reqsai.iam.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link User}. Backs {@code UserRepositoryAdapter}. */
public interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByAccountId(UUID accountId);
}
