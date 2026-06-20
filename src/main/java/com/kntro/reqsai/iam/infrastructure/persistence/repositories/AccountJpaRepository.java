package com.kntro.reqsai.iam.infrastructure.persistence.repositories;

import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Account}. Backs {@code AccountRepositoryAdapter} and triggers
 * domain-event publication on {@code save()} (via {@code @DomainEvents}).
 */
public interface AccountJpaRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmail(Email email);

    Optional<Account> findByEmail(Email email);
}
