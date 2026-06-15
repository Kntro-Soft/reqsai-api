package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the UserStory aggregate root (manual creation slice).
 *
 * @see UserStory
 */
@DisplayName("Domain: UserStory Aggregate")
class UserStoryTest {

    @Test
    @DisplayName("should create a manual story in DRAFT with no originating session")
    void should_create_in_draft() {
        // Act
        UserStory story = UserStoryMother.draft().build();

        // Assert
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
        assertThat(story.getId()).isNotNull();
        assertThat(story.getProjectId()).isNotNull();
        assertThat(story.getSessionId()).isNull();
    }

    @Test
    @DisplayName("should register UserStoryCreatedEvent on creation")
    void should_register_created_event() {
        // Act
        UserStory story = UserStoryMother.draft().build();

        // Assert
        assertThat(AggregateEvents.of(story))
                .anySatisfy(e -> assertThat(e).isInstanceOf(UserStoryCreatedEvent.class));
    }

    @Test
    @DisplayName("should reject a blank title")
    void should_reject_blank_title() {
        // Act & Assert
        assertThatThrownBy(() -> UserStoryMother.draft().withTitle("  ").build())
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject negative story points")
    void should_reject_negative_story_points() {
        // Act & Assert
        assertThatThrownBy(() -> UserStoryMother.draft().withStoryPoints(-1).build())
                .isInstanceOf(DomainException.class);
    }
}
