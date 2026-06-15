package com.kntro.reqsai.discovery.mothers;

/**
 * Object Mother for {@link com.kntro.reqsai.discovery.domain.model.UserStory} — named scenarios
 * returning a {@link UserStoryBuilder} for further customization.
 */
public final class UserStoryMother {

    private UserStoryMother() {
    }

    /** A freshly created story in {@code DRAFT}. */
    public static UserStoryBuilder draft() {
        return UserStoryBuilder.aUserStory();
    }
}
