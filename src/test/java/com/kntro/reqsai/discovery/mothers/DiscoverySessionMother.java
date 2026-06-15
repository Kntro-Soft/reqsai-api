package com.kntro.reqsai.discovery.mothers;

/**
 * Object Mother for {@link com.kntro.reqsai.discovery.domain.model.DiscoverySession} — named scenarios
 * returning a {@link DiscoverySessionBuilder} for further customization.
 */
public final class DiscoverySessionMother {

    private DiscoverySessionMother() {
    }

    /** A freshly created session in {@code DRAFT}. */
    public static DiscoverySessionBuilder draft() {
        return DiscoverySessionBuilder.aSession();
    }
}
