package com.kntro.reqsai.discovery.mothers;

import com.kntro.reqsai.discovery.application.command.UpdateUserStoryCommand;
import com.kntro.reqsai.discovery.domain.model.Priority;
import net.datafaker.Faker;

import java.util.UUID;

/** Object Mother for {@link UpdateUserStoryCommand} — random valid edits plus invalid inputs. */
public final class UpdateUserStoryCommandMother {

    private static final Faker FAKER = new Faker();

    private UpdateUserStoryCommandMother() {
    }

    public static UpdateUserStoryCommand forStoryInProject(UUID projectId, UUID storyId) {
        return new UpdateUserStoryCommand(
                projectId, storyId, FAKER.lorem().sentence(3), FAKER.job().position(),
                FAKER.lorem().sentence(5), FAKER.lorem().sentence(6),
                FAKER.options().option(Priority.class), FAKER.number().numberBetween(1, 13));
    }

    public static UpdateUserStoryCommand valid() {
        return forStoryInProject(UUID.randomUUID(), UUID.randomUUID());
    }
}
