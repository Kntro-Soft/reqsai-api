package com.kntro.reqsai.discovery.infrastructure.billing;

import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiUsageReporter;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TokenUsageRecorderPort} adapter that hands every model call to the {@link AiUsageReporter}, which
 * writes the usage ledger and the monthly counter of the current tenant's organization. The feature and
 * project come from the surrounding {@code AiUsageScope}.
 * <p>
 * Best-effort by contract: the reporter never throws and never blocks, so metering cannot interfere with
 * requirement generation.
 */
@Component
@RequiredArgsConstructor
public class BillingTokenUsageRecorder implements TokenUsageRecorderPort {

    private final AiUsageReporter reporter;

    @Override
    public void record(long totalTokens) {
        if (totalTokens > 0) {
            reporter.report(TokenUsage.of(AiFeature.OTHER, null, null, totalTokens, 0, 0, null));
        }
    }

    @Override
    public void record(TokenUsage usage) {
        reporter.report(usage);
    }
}
