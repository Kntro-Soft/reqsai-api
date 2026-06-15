package com.kntro.reqsai.discovery.mothers;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import net.datafaker.Faker;

import java.util.UUID;

/**
 * Fluent builder for {@link DiscoverySession} instances in tests, with Datafaker random-valid defaults.
 * A test sets only the fields it asserts on.
 */
public class DiscoverySessionBuilder {

    private static final Faker FAKER = new Faker();

    private UUID projectId = UUID.randomUUID();
    private String title = FAKER.lorem().sentence(3);
    private LanguageCode language = LanguageCode.of(FAKER.options().option("es-PE", "en-US", "pt-BR"));

    public static DiscoverySessionBuilder aSession() {
        return new DiscoverySessionBuilder();
    }

    public DiscoverySessionBuilder withProjectId(UUID projectId) {
        this.projectId = projectId;
        return this;
    }

    public DiscoverySessionBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public DiscoverySessionBuilder withLanguage(String code) {
        this.language = LanguageCode.of(code);
        return this;
    }

    public DiscoverySession build() {
        return new DiscoverySession(projectId, title, language);
    }
}
