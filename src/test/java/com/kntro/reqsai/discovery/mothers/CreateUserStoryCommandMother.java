package com.kntro.reqsai.discovery.mothers;

import com.kntro.reqsai.discovery.application.command.CreateUserStoryCommand;
import com.kntro.reqsai.discovery.domain.model.Priority;
import net.datafaker.Faker;

import java.util.UUID;

/** Object Mother for {@link CreateUserStoryCommand} — random valid scenarios plus invalid inputs. */
public final class CreateUserStoryCommandMother {

    private static final Faker FAKER = new Faker();

    private CreateUserStoryCommandMother() {
    }

    public static CreateUserStoryCommand valid() {
        return new CreateUserStoryCommand(
                UUID.randomUUID(), FAKER.lorem().sentence(3), FAKER.job().position(),
                FAKER.lorem().sentence(5), FAKER.lorem().sentence(6),
                FAKER.options().option(Priority.class), FAKER.number().numberBetween(1, 13));
    }

    public static CreateUserStoryCommand withBlankTitle() {
        return new CreateUserStoryCommand(
                UUID.randomUUID(), "   ", FAKER.job().position(),
                FAKER.lorem().sentence(5), FAKER.lorem().sentence(6), Priority.MEDIUM, null);
    }

    public static CreateUserStoryCommand withNegativeStoryPoints() {
        return new CreateUserStoryCommand(
                UUID.randomUUID(), FAKER.lorem().sentence(3), FAKER.job().position(),
                FAKER.lorem().sentence(5), FAKER.lorem().sentence(6), Priority.LOW, -3);
    }
}
