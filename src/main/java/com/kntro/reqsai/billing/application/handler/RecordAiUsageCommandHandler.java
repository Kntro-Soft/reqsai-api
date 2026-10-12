package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.RecordAiUsageCommand;
import com.kntro.reqsai.billing.application.port.AiUsageLedgerPort;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.application.port.TokenCounterState;
import com.kntro.reqsai.billing.domain.model.PlanCatalog;
import com.kntro.reqsai.billing.domain.model.valueobjects.QuotaWarningLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Handles {@link RecordAiUsageCommand}: appends the call to the ledger and, in the same transaction, adds
 * its quota weight to the monthly counter with an atomic increment. A call whose idempotency key is
 * already in the ledger changes nothing, so a retried report never counts twice and the counter always
 * equals the sum of the ledger's {@code quota_tokens} for the period.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecordAiUsageCommandHandler {

    private final AiUsageLedgerPort ledger;
    private final SubscriptionRepositoryPort subscriptions;

    /**
     * @param counter       the counter after the increment, empty when nothing was added to it
     * @param newWarning    the warning step this call reached for the first time this period, or {@code null}
     * @param limit         the plan's monthly allowance, 0 when the counter was not touched
     */
    public record Outcome(Optional<TokenCounterState> counter, @Nullable QuotaWarningLevel newWarning, long limit) {

        static Outcome nothing() {
            return new Outcome(Optional.empty(), null, 0);
        }
    }

    @Transactional
    public Outcome handle(RecordAiUsageCommand command) {
        Instant now = Instant.now();
        if (!ledger.append(command.usage(), now)) {
            log.debug("AI usage {} already recorded; ignoring", command.usage().idempotencyKey());
            return Outcome.nothing();
        }
        long quotaTokens = command.usage().quotaTokens();
        if (quotaTokens <= 0) {
            return Outcome.nothing();
        }
        Optional<TokenCounterState> counter = subscriptions.addTokens(command.organizationId(), quotaTokens, now);
        if (counter.isEmpty()) {
            log.warn("No subscription for org {}; {} quota tokens are in the ledger only",
                    command.organizationId(), quotaTokens);
            return Outcome.nothing();
        }
        long limit = PlanCatalog.limitsFor(counter.get().planType()).maxTokensPerMonth();
        QuotaWarningLevel level = QuotaWarningLevel.of(counter.get().used(), limit);
        boolean first = level != QuotaWarningLevel.NONE
                && subscriptions.raiseWarningPercent(command.organizationId(), level.percent());
        return new Outcome(counter, first ? level : null, limit);
    }
}
