package com.kntro.reqsai.discovery.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: user story review time and generated baseline (quality indicators)")
class UserStoryQualityTrackingTest {

    private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-11T10:00:00Z");

    private static UserStory story() {
        return new UserStory(UUID.randomUUID(), UUID.randomUUID(), "Reservar mesa", "comensal", "reservar una mesa",
                "no hacer fila", Priority.HIGH, 3);
    }

    @Test
    @DisplayName("a new story has no review time and no generated baseline")
    void startsEmpty() {
        UserStory story = story();

        assertThat(story.getReviewedAt()).isNull();
        assertThat(story.getGeneratedText()).isNull();
    }

    @Test
    @DisplayName("approving or rejecting records the instant of the decision")
    void decisionIsTimed() {
        UserStory approved = story();
        approved.changeReviewStatus(StoryStatus.APPROVED, T1);
        UserStory rejected = story();
        rejected.changeReviewStatus(StoryStatus.REJECTED, T2);

        assertThat(approved.getReviewedAt()).isEqualTo(T1);
        assertThat(rejected.getReviewedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("the decision without an instant is timed with the current time")
    void decisionNow() {
        Instant before = Instant.now();
        UserStory story = story();

        story.changeReviewStatus(StoryStatus.APPROVED);

        assertThat(story.getReviewedAt()).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("changing the decision moves the review time; repeating it keeps the first instant")
    void decisionRevised() {
        UserStory story = story();
        story.changeReviewStatus(StoryStatus.APPROVED, T1);

        story.changeReviewStatus(StoryStatus.APPROVED, T2);
        assertThat(story.getReviewedAt()).isEqualTo(T1);

        story.changeReviewStatus(StoryStatus.REJECTED, T2);
        assertThat(story.getReviewedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("sending the story back to draft clears the review time")
    void backToDraft() {
        UserStory story = story();
        story.changeReviewStatus(StoryStatus.APPROVED, T1);

        story.changeReviewStatus(StoryStatus.DRAFT, T2);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
        assertThat(story.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("an invalid decision changes neither the status nor the review time")
    void invalidDecision() {
        UserStory story = story();
        story.changeReviewStatus(StoryStatus.APPROVED, T1);

        assertThatThrownBy(() -> story.changeReviewStatus(StoryStatus.MERGED, T2))
                .isInstanceOf(com.kntro.reqsai.shared.domain.exception.DomainException.class);
        assertThat(story.getReviewedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("the generated baseline freezes the text now, criteria included, and later edits do not move it")
    void baselineFreezes() {
        UserStory story = story();
        story.addAcceptanceCriterion(null, "un horario", "reserva", "queda confirmada");
        story.captureGeneratedText();
        String proposed = story.getGeneratedText();

        story.update("Otro título", "mesero", "otra acción", "otro beneficio", Priority.LOW, 1);

        assertThat(proposed).isEqualTo("Reservar mesa\ncomensal\nreservar una mesa\nno hacer fila\n"
                + "un horario | reserva | queda confirmada");
        assertThat(story.getGeneratedText()).isEqualTo(proposed);
        assertThat(story.currentText()).isNotEqualTo(proposed).startsWith("Otro título");
    }

    @Test
    @DisplayName("capturing again re-bases the baseline on what the story says now")
    void baselineRebased() {
        UserStory story = story();
        story.captureGeneratedText();
        story.addAcceptanceCriterion(null, "a", "b", "c");

        story.captureGeneratedText();

        assertThat(story.getGeneratedText()).isEqualTo(story.currentText());
    }

    @Test
    @DisplayName("a baseline can be given as the text the AI proposed; it cannot be blank")
    void explicitBaseline() {
        UserStory story = story();

        story.captureGeneratedText("propuesta de la IA");

        assertThat(story.getGeneratedText()).isEqualTo("propuesta de la IA");
        assertThatThrownBy(() -> story.captureGeneratedText(" "))
                .isInstanceOf(com.kntro.reqsai.shared.domain.exception.DomainException.class);
    }
}
