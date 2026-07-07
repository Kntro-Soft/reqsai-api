package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link IntegrationProvider} for a given {@link IntegrationProviderType} (ADR-0022 provider
 * seam). Indexes every provider bean by its {@code type()}; adding a provider is purely additive.
 */
@Component
public class ProviderRegistry {

    private final Map<IntegrationProviderType, IntegrationProvider> byType =
            new EnumMap<>(IntegrationProviderType.class);

    public ProviderRegistry(List<IntegrationProvider> providers) {
        providers.forEach(p -> byType.put(p.type(), p));
    }

    /** Returns the provider for {@code type}, or throws if none is registered. */
    public IntegrationProvider get(IntegrationProviderType type) {
        IntegrationProvider provider = byType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No integration provider registered for " + type);
        }
        return provider;
    }
}
