package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application: Demo discovery content")
class DemoDiscoveryContentTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Test
    @DisplayName("builds a completed session with its transcript")
    void builds_completed_session() {
        DemoDiscoveryContent.Content content = DemoDiscoveryContent.build(PROJECT_ID);

        assertThat(content.session().getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(content.session().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(content.session().getTitle()).isEqualTo(DemoDiscoveryContent.SESSION_TITLE);
        assertThat(content.session().getLanguage()).isEqualTo(DemoDiscoveryContent.LANGUAGE);
        assertThat(content.session().getTranscript()).isNotBlank();
        assertThat(content.session().getAudioDurationMs()).isPositive();
        assertThat(content.session().getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("lays out final, diarized, ordered transcript segments that make up the transcript")
    void builds_segments() {
        DemoDiscoveryContent.Content content = DemoDiscoveryContent.build(PROJECT_ID);

        assertThat(content.segments()).hasSize(DemoDiscoveryContent.segmentCount());
        assertThat(content.segments()).allSatisfy(segment -> {
            assertThat(segment.getSessionId()).isEqualTo(content.session().getId());
            assertThat(segment.isFinal()).isTrue();
            assertThat(segment.getSpeakerLabel()).isIn("0", "1");
            assertThat(content.session().getTranscript()).contains(segment.getText());
        });
        assertThat(content.segments()).extracting(TranscriptSegment::getSequence)
                .isSorted().doesNotHaveDuplicates().first().isEqualTo(1);
        assertThat(content.segments()).extracting(TranscriptSegment::getSpeakerLabel)
                .containsOnly("0", "1");
        for (int i = 1; i < content.segments().size(); i++) {
            assertThat(content.segments().get(i).getStartMs())
                    .isGreaterThan(content.segments().get(i - 1).getEndMs());
        }
    }

    @Test
    @DisplayName("builds session stories with acceptance criteria in DRAFT and APPROVED")
    void builds_stories() {
        DemoDiscoveryContent.Content content = DemoDiscoveryContent.build(PROJECT_ID);

        assertThat(content.stories()).hasSize(DemoDiscoveryContent.storyCount()).hasSizeBetween(5, 6);
        assertThat(content.stories()).allSatisfy(story -> {
            assertThat(story.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(story.getSessionId()).isEqualTo(content.session().getId());
            assertThat(story.getAcceptanceCriteria()).isNotEmpty().allSatisfy(criterion -> {
                assertThat(criterion.getGiven()).isNotBlank();
                assertThat(criterion.getWhen()).isNotBlank();
                assertThat(criterion.getThen()).isNotBlank();
            });
            assertThat(story.isIndexed()).isFalse();
        });
        assertThat(content.stories()).extracting(UserStory::getStatus)
                .contains(StoryStatus.DRAFT, StoryStatus.APPROVED)
                .containsOnly(StoryStatus.DRAFT, StoryStatus.APPROVED);
        assertThat(content.stories()).extracting(UserStory::getTitle).doesNotHaveDuplicates();
        assertThat(content.stories())
                .flatExtracting(UserStory::getAcceptanceCriteria)
                .extracting(AcceptanceCriterion::getScenario)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("leaves a new-story and a clarifying-question suggestion pending")
    void builds_pending_suggestions() {
        DemoDiscoveryContent.Content content = DemoDiscoveryContent.build(PROJECT_ID);

        assertThat(content.suggestions()).hasSize(2).allSatisfy(suggestion -> {
            assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.PENDING);
            assertThat(suggestion.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(suggestion.getSessionId()).isEqualTo(content.session().getId());
        });
        assertThat(content.suggestions()).extracting(s -> s.getType())
                .containsExactly(SuggestionType.NEW_STORY, SuggestionType.CLARIFYING_QUESTION);
        assertThat(content.suggestions().get(0).getDraftAcceptanceCriteria()).isNotEmpty();
        assertThat(content.suggestions().get(1).getQuestion()).isNotBlank();
    }

    @Test
    @DisplayName("every build yields fresh identities")
    void builds_fresh_aggregates() {
        DemoDiscoveryContent.Content first = DemoDiscoveryContent.build(PROJECT_ID);
        DemoDiscoveryContent.Content second = DemoDiscoveryContent.build(PROJECT_ID);

        assertThat(first.session().getId()).isNotEqualTo(second.session().getId());
        assertThat(first.stories().get(0).getId()).isNotEqualTo(second.stories().get(0).getId());
    }
}
