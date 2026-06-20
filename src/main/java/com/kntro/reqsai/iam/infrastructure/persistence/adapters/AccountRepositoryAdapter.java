package com.kntro.reqsai.iam.infrastructure.persistence.adapters;

import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.infrastructure.persistence.repositories.AccountJpaRepository;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link AccountRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    @Override
    public Account save(Account account) {
        return jpa.save(account);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public Optional<Account> findByEmail(Email email) {
        return jpa.findByEmail(email);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpa.findById(id);
    }
}
