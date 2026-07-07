package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.RecordTokenConsumptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link RecordTokenConsumptionCommand}: increments the current-period token counter on the
 * organization's subscription. Best-effort — a missing subscription is logged and ignored so AI usage
 * is never blocked by a metering gap.
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
        subscriptions.findByOrganizationId(command.organizationId()).ifPresentOrElse(
                subscription -> {
                    subscription.recordTokenUsage(command.tokens());
                    subscriptions.save(subscription);
                    log.debug("Recorded {} tokens for org {} (period total {})",
                            command.tokens(), command.organizationId(), subscription.getTokenQuotaUsed());
                },
                () -> log.warn("No subscription for org {}; skipping token metering of {} tokens",
                        command.organizationId(), command.tokens())
        );
    }
}
