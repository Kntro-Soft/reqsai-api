package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import org.jspecify.annotations.Nullable;

/**
 * Thrown by a {@link RequirementGenerationPort} when the model DID reply but its output is not the expected
 * structured result — an empty reply, an answer, code, prose: anything that is not the JSON contract.
 *
 * <p>Distinct from a provider failure (network, timeout, quota), which surfaces as any other exception, so a
 * caller can tell "this transcript window makes the model misbehave" (retrying the same window forever is
 * pointless) from "the provider is unreachable right now" (retrying later is right). It carries the same
 * {@link DiscoveryError#REQUIREMENT_GENERATION_FAILED} code as every other generation failure, so the HTTP
 * mapping and the error code clients see are unchanged.
 */
public class UnparseableGenerationException extends InfrastructureException {

    public UnparseableGenerationException(String message, @Nullable Throwable cause) {
        super(DiscoveryError.REQUIREMENT_GENERATION_FAILED, message, cause);
    }
}
