package com.kntro.reqsai.discovery.mothers;

import com.kntro.reqsai.discovery.application.command.CreateDiscoverySessionCommand;
import net.datafaker.Faker;

import java.util.UUID;

/**
 * Object Mother for {@link CreateDiscoverySessionCommand} — random valid scenarios plus invalid inputs.
 */
public final class CreateDiscoverySessionCommandMother {

    private static final Faker FAKER = new Faker();

    private CreateDiscoverySessionCommandMother() {
    }

    public static CreateDiscoverySessionCommand valid() {
        return new CreateDiscoverySessionCommand(
                UUID.randomUUID(), FAKER.lorem().sentence(3),
                FAKER.options().option("es-PE", "en-US", "pt-BR"));
    }

    public static CreateDiscoverySessionCommand withLanguage(String language) {
        return new CreateDiscoverySessionCommand(
                UUID.randomUUID(), FAKER.lorem().sentence(3), language);
    }

    public static CreateDiscoverySessionCommand withInvalidLanguage() {
        return withLanguage("english");
    }
}
