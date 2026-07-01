package com.kntro.reqsai.iam.application.notification;

import com.kntro.reqsai.iam.api.AccountVerifiedIntegrationEvent;
import com.kntro.reqsai.iam.domain.event.AccountVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Relays the internal {@link AccountVerifiedEvent} to the public {@link AccountVerifiedIntegrationEvent}
 * in the {@code iam::api} named interface, so other modules can react to a verified account without
 * importing IAM's domain event. Keeps the IAM domain layer free of cross-module wiring.
 */
@Component
@RequiredArgsConstructor
class AccountVerifiedEventRelay {

    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onAccountVerified(AccountVerifiedEvent event) {
        events.publishEvent(AccountVerifiedIntegrationEvent.of(event.accountId(), event.email()));
    }
}
