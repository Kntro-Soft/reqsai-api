package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoveryInsightsRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.insights.HeldSession;
import com.kntro.reqsai.discovery.insights.QualityEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: discovery insights, quality evidence")
@ExtendWith(MockitoExtension.class)
class DiscoveryInsightsQualityEvidenceTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    private DiscoveryInsightsRepository insights;

    private static UserStory story(boolean fromAi, StoryStatus decision) {
        UserStory story = new UserStory(UUID.randomUUID(), PROJECT, "Reservar mesa", "comensal", "reservar una mesa",
                "no hacer fila", Priority.HIGH, 3);
        if (fromAi) {
            story.captureGeneratedText();
        }
        story.changeReviewStatus(decision);
        return story;
    }

    @Test
    @DisplayName("stories with a generated baseline carry both texts; the others are only counted")
    void splitsStoriesByBaseline() {
        UserStory kept = story(true, StoryStatus.APPROVED);
        UserStory rejected = story(true, StoryStatus.REJECTED);
        UserStory manual = story(false, StoryStatus.APPROVED);
        when(insights.storiesDecidedBetween(PROJECT, FROM, TO)).thenReturn(List.of(kept, manual, rejected));
        when(insights.sessionsEndedBetween(PROJECT, FROM, TO)).thenReturn(List.of());

        QualityEvidence evidence = new DiscoveryInsightsPortImpl(insights).qualityEvidence(PROJECT, FROM, TO);

        assertThat(evidence.decidedWithoutBaseline()).isEqualTo(1);
        assertThat(evidence.decidedStories()).hasSize(2);
        assertThat(evidence.decidedStories().getFirst().storyId()).isEqualTo(kept.getId());
        assertThat(evidence.decidedStories().getFirst().status()).isEqualTo("APPROVED");
        assertThat(evidence.decidedStories().getFirst().generatedText()).isEqualTo(kept.getGeneratedText());
        assertThat(evidence.decidedStories().getFirst().currentText()).isEqualTo(kept.currentText());
        assertThat(evidence.decidedStories().getLast().status()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("the meetings of the period pass through untouched")
    void passesSessionsThrough() {
        HeldSession held = new HeldSession(UUID.randomUUID(), "Kickoff", "CLARIFICATION", FROM.plusSeconds(60), null);
        when(insights.storiesDecidedBetween(PROJECT, FROM, TO)).thenReturn(List.of());
        when(insights.sessionsEndedBetween(PROJECT, FROM, TO)).thenReturn(List.of(held));

        QualityEvidence evidence = new DiscoveryInsightsPortImpl(insights).qualityEvidence(PROJECT, FROM, TO);

        assertThat(evidence.sessions()).containsExactly(held);
        assertThat(evidence.decidedStories()).isEmpty();
        assertThat(evidence.decidedWithoutBaseline()).isZero();
    }
}
