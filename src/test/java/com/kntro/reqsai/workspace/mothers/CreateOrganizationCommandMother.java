package com.kntro.reqsai.workspace.mothers;

import com.kntro.reqsai.workspace.application.command.CreateOrganizationCommand;
import net.datafaker.Faker;

import java.util.UUID;

/**
 * Object Mother for {@link CreateOrganizationCommand} — named scenarios with random valid data plus a
 * family of invalid-input factories.
 */
public final class CreateOrganizationCommandMother {

    private static final Faker FAKER = new Faker();

    private CreateOrganizationCommandMother() {
    }

    /** A fully valid command (slug derived from the name, random valid language). */
    public static CreateOrganizationCommand valid() {
        return new CreateOrganizationCommand(
                FAKER.company().name(), null,
                FAKER.options().option("es-PE", "en-US", "pt-BR"), UUID.randomUUID());
    }

    /** Valid command with only the required fields (slug + language omitted). */
    public static CreateOrganizationCommand minimal() {
        return new CreateOrganizationCommand(FAKER.company().name(), null, null, UUID.randomUUID());
    }

    /** Valid command with a specific name and a deterministic {@code en-US} language. */
    public static CreateOrganizationCommand withName(String name) {
        return new CreateOrganizationCommand(name, null, "en-US", UUID.randomUUID());
    }

    /** Valid command with an explicit slug. */
    public static CreateOrganizationCommand withSlug(String slug) {
        return new CreateOrganizationCommand(FAKER.company().name(), slug, null, UUID.randomUUID());
    }

    // Invalid inputs

    public static CreateOrganizationCommand withBlankName() {
        return new CreateOrganizationCommand("   ", null, null, UUID.randomUUID());
    }

    public static CreateOrganizationCommand withInvalidSlug() {
        return new CreateOrganizationCommand(FAKER.company().name(), "Invalid Slug!", null, UUID.randomUUID());
    }

    public static CreateOrganizationCommand withInvalidLanguage() {
        return new CreateOrganizationCommand(FAKER.company().name(), null, "english", UUID.randomUUID());
    }
}
