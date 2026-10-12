package com.kntro.reqsai.billing.application.notification;

import com.kntro.reqsai.billing.domain.model.valueobjects.QuotaWarningLevel;
import com.kntro.reqsai.billing.interfaces.notification.QuotaWarningMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pushes the "you reached 80 / 90 / 100 percent of your AI allowance" message to everyone viewing the
 * organization. Called once per level per billing period (the caller won the conditional UPDATE that
 * records the level), and never throws: a notification must not undo a recorded usage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuotaWarningNotifier {

    private final RealtimeNotifier notifier;

    /** Logical topic of the organization's usage messages; the notifier adds the {@code /topic} prefix. */
    public static String topicOf(UUID organizationId) {
        return "organizations/" + organizationId + "/usage";
    }

    public void warn(UUID organizationId, QuotaWarningLevel level, long used, long limit) {
        try {
            notifier.broadcast(topicOf(organizationId),
                    QuotaWarningMessage.of(organizationId, level.name(), level.percent(), used, limit));
            log.info("Org {} reached {}% of its AI allowance ({} of {} tokens)", organizationId, level.percent(),
                    used, limit);
        } catch (RuntimeException e) {
            log.warn("Could not push the {} quota warning for org {}: {}", level, organizationId, e.getMessage());
        }
    }
}
