package com.kntro.reqsai.billing.infrastructure.payment.stripe;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the webhook idempotency ledger. Package-private to keep persistence
 * details encapsulated.
 */
interface ProcessedStripeEventJpaRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
