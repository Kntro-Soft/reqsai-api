package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import java.util.UUID;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    @DisplayName("should add a new acceptance criterion")
    void should_add_acceptance_criterion() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();

        // Act
        AcceptanceCriterion criterion = story.addAcceptanceCriterion("scenario", "given", "when", "then");

        // Assert
        assertThat(story.getAcceptanceCriteria()).hasSize(1);
        assertThat(story.getAcceptanceCriteria().get(0)).isEqualTo(criterion);
        assertThat(criterion.getStory()).isEqualTo(story);
        assertThat(criterion.getScenario()).isEqualTo("scenario");
        assertThat(criterion.getGiven()).isEqualTo("given");
        assertThat(criterion.getWhen()).isEqualTo("when");
        assertThat(criterion.getThen()).isEqualTo("then");
    }

    @Test
    @DisplayName("should update an existing acceptance criterion")
    void should_update_acceptance_criterion() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();
        AcceptanceCriterion criterion = story.addAcceptanceCriterion("scenario", "given", "when", "then");

        // Act
        story.updateAcceptanceCriterion(criterion.getId(), "updated scenario", "updated given", "updated when", "updated then");

        // Assert
        assertThat(criterion.getScenario()).isEqualTo("updated scenario");
        assertThat(criterion.getGiven()).isEqualTo("updated given");
        assertThat(criterion.getWhen()).isEqualTo("updated when");
        assertThat(criterion.getThen()).isEqualTo("updated then");
    }

    @Test
    @DisplayName("should throw error when updating non-existent criterion")
    void should_throw_when_updating_non_existent_criterion() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();

        // Act & Assert
        assertThatThrownBy(() -> story.updateAcceptanceCriterion(UUID.randomUUID(), "scenario", "given", "when", "then"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should remove an existing acceptance criterion")
    void should_remove_acceptance_criterion() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();
        AcceptanceCriterion criterion = story.addAcceptanceCriterion("scenario", "given", "when", "then");
        assertThat(story.getAcceptanceCriteria()).hasSize(1);

        // Act
        story.removeAcceptanceCriterion(criterion.getId());

        // Assert
        assertThat(story.getAcceptanceCriteria()).isEmpty();
    }

    @Test
    @DisplayName("should throw error when removing non-existent criterion")
    void should_throw_when_removing_non_existent_criterion() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();

        // Act & Assert
        assertThatThrownBy(() -> story.removeAcceptanceCriterion(UUID.randomUUID()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should approve, reject and send a story back to draft")
    void should_move_between_review_statuses() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();

        // Act & Assert
        story.changeReviewStatus(StoryStatus.APPROVED);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.APPROVED);

        story.changeReviewStatus(StoryStatus.REJECTED);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.REJECTED);

        story.changeReviewStatus(StoryStatus.DRAFT);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
    }

    @Test
    @DisplayName("should keep the status when the same review decision is repeated")
    void should_be_idempotent_for_same_status() {
        // Arrange
        UserStory story = UserStoryMother.draft().build();
        story.changeReviewStatus(StoryStatus.APPROVED);

        // Act
        story.changeReviewStatus(StoryStatus.APPROVED);

        // Assert
        assertThat(story.getStatus()).isEqualTo(StoryStatus.APPROVED);
    }

    @ParameterizedTest
    @EnumSource(value = StoryStatus.class, names = {"MERGED", "EXPORTED"})
    @DisplayName("should reject a review that targets MERGED or EXPORTED")
    void should_reject_non_review_target(StoryStatus target) {
        // Arrange
        UserStory story = UserStoryMother.draft().build();

        // Act & Assert
        assertThatThrownBy(() -> story.changeReviewStatus(target))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.INVALID_STORY_STATUS));
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
    }

    @ParameterizedTest
    @EnumSource(value = StoryStatus.class, names = {"MERGED", "EXPORTED"})
    @DisplayName("should not review a story that was merged or exported")
    void should_not_review_story_out_of_review(StoryStatus current) {
        // Arrange
        UserStory story = UserStoryMother.draft().build();
        ReflectionTestUtils.setField(story, "status", current);

        // Act & Assert
        assertThatThrownBy(() -> story.changeReviewStatus(StoryStatus.APPROVED))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.INVALID_STORY_STATUS));
        assertThat(story.getStatus()).isEqualTo(current);
    }
}
