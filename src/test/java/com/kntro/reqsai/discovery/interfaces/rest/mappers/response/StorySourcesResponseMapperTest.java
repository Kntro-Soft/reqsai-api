package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.StorySources;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StorySourcesResponse;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Interfaces: Story Sources Response Mapper")
class StorySourcesResponseMapperTest {

    @Test
    @DisplayName("a fragment reports the moment of the meeting: start of its first segment, end of its last")
    void fragment_reports_the_moment_of_the_meeting() {
        DiscoverySession session = DiscoverySessionMother.draft().withTitle("Kickoff").build();
        Instant start = Instant.parse("2026-11-10T15:00:00Z");
        session.startRecording(start);
        UserStory story = new UserStory(session.getId(), session.getProjectId(), "Reservar", "cliente",
                "reservar", "ir", Priority.HIGH, 3);
        story.recordOrigin(session.getId(), 2, "reservar una mesa", null);
        var first = new TranscriptSegment(session.getId(), 2, "0", "Quiero reservar", 5_000, 8_000, true);
        var last = new TranscriptSegment(session.getId(), 3, "0", "una mesa en línea.", 8_000, 10_500, true);
        var fragment = new StorySources.Fragment(story.getSourceFragments().getFirst(), session,
                List.of(first, last), "Ana Torres");

        StorySourcesResponse response = StorySourcesResponseMapper.toResponse(
                new StorySources(story.getId(), StorySources.Origin.SESSION, null, null, List.of(fragment)));

        assertThat(response.origin()).isEqualTo("SESSION");
        assertThat(response.fragments()).singleElement().satisfies(f -> {
            assertThat(f.sessionTitle()).isEqualTo("Kickoff");
            assertThat(f.sessionStartedAt()).isEqualTo(start);
            assertThat(f.quote()).isEqualTo("reservar una mesa");
            assertThat(f.transcriptText()).isEqualTo("Quiero reservar una mesa en línea.");
            assertThat(f.startSequence()).isEqualTo(2);
            assertThat(f.endSequence()).isEqualTo(3);
            assertThat(f.startMs()).isEqualTo(5_000L);
            assertThat(f.endMs()).isEqualTo(10_500L);
            assertThat(f.occurredAt()).isEqualTo(start.plusMillis(5_000));
            assertThat(f.speakerName()).isEqualTo("Ana Torres");
        });
    }

    @Test
    @DisplayName("a fragment that was not located keeps its quote and has no moment")
    void unlocated_fragment_has_no_moment() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UserStory story = new UserStory(session.getId(), session.getProjectId(), "Reservar", "cliente",
                "reservar", "ir", Priority.HIGH, 3);
        story.recordOrigin(session.getId(), null, "una cita sin ubicar", null);
        var fragment = new StorySources.Fragment(story.getSourceFragments().getFirst(), session, List.of(), null);

        StorySourcesResponse response = StorySourcesResponseMapper.toResponse(
                new StorySources(story.getId(), StorySources.Origin.SESSION, null, null, List.of(fragment)));

        assertThat(response.fragments()).singleElement().satisfies(f -> {
            assertThat(f.quote()).isEqualTo("una cita sin ubicar");
            assertThat(f.transcriptText()).isNull();
            assertThat(f.startMs()).isNull();
            assertThat(f.endMs()).isNull();
            assertThat(f.occurredAt()).isNull();
            assertThat(f.startSequence()).isNull();
        });
    }

    @Test
    @DisplayName("manual and unavailable origins have no fragments; unavailable still names the session")
    void origins_without_fragments() {
        DiscoverySession session = DiscoverySessionMother.draft().withTitle("Kickoff").build();
        var story = new UserStory(session.getId(), session.getProjectId(), "Reservar", "cliente",
                "reservar", "ir", Priority.HIGH, 3);

        StorySourcesResponse manual = StorySourcesResponseMapper.toResponse(
                new StorySources(story.getId(), StorySources.Origin.MANUAL, null, null, List.of()));
        StorySourcesResponse unavailable = StorySourcesResponseMapper.toResponse(
                new StorySources(story.getId(), StorySources.Origin.UNAVAILABLE, session.getId(), session, List.of()));

        assertThat(manual.origin()).isEqualTo("MANUAL");
        assertThat(manual.fragments()).isEmpty();
        assertThat(manual.sessionId()).isNull();
        assertThat(unavailable.origin()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.sessionId()).isEqualTo(session.getId());
        assertThat(unavailable.sessionTitle()).isEqualTo("Kickoff");
        assertThat(unavailable.fragments()).isEmpty();
    }
}
