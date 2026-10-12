package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.RecordTokenConsumptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles {@link RecordTokenConsumptionCommand}: adds tokens to the current-period counter of the
 * organization's subscription with an atomic increment, so concurrent calls never lose an update.
 * Best-effort — a missing subscription is logged and ignored so AI usage is never blocked by a metering
 * gap. This is the counter-only path (no ledger row); AI calls report through {@link RecordAiUsageCommandHandler}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordTokenConsumptionCommandHandler {

    private final SubscriptionRepositoryPort subscriptions;

    public void handle(RecordTokenConsumptionCommand command) {
        if (command.tokens() <= 0) {
            return;
        }
        subscriptions.addTokens(command.organizationId(), command.tokens(), Instant.now()).ifPresentOrElse(
                state -> log.debug("Recorded {} tokens for org {} (period total {})",
                        command.tokens(), command.organizationId(), state.used()),
                () -> log.warn("No subscription for org {}; skipping token metering of {} tokens",
                        command.organizationId(), command.tokens())
        );
    }
}
