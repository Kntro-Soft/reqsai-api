package com.kntro.reqsai.discovery.mothers;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import net.datafaker.Faker;

import java.util.UUID;

/**
 * Fluent builder for {@link UserStory} instances in tests, with Datafaker random-valid defaults.
 * A test sets only the fields it asserts on.
 */
public class UserStoryBuilder {

    private static final Faker FAKER = new Faker();

    private UUID projectId = UUID.randomUUID();
    private String title = FAKER.lorem().sentence(3);
    private final String role = FAKER.job().position();
    private final String action = FAKER.lorem().sentence(5);
    private final String benefit = FAKER.lorem().sentence(6);
    private Priority priority = FAKER.options().option(Priority.class);
    private Integer storyPoints = FAKER.number().numberBetween(1, 13);

    public static UserStoryBuilder aUserStory() {
        return new UserStoryBuilder();
    }

    public UserStoryBuilder withProjectId(UUID projectId) {
        this.projectId = projectId;
        return this;
    }

    public UserStoryBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public UserStoryBuilder withPriority(Priority priority) {
        this.priority = priority;
        return this;
    }

    public UserStoryBuilder withStoryPoints(Integer storyPoints) {
        this.storyPoints = storyPoints;
        return this;
    }

    public UserStory build() {
        return new UserStory(projectId, title, role, action, benefit, priority, storyPoints);
    }
}
