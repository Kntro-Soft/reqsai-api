package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetStorySourcesQuery;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.domain.model.SpeakerSpan;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Story Sources")
@ExtendWith(MockitoExtension.class)
class GetStorySourcesQueryHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private TranscriptSegmentRepository segments;
    @Mock
    private SessionSpeakerService speakers;
    @InjectMocks
    private GetStorySourcesQueryHandler handler;

    private static UserStory generatedIn(DiscoverySession session) {
        return new UserStory(session.getId(), session.getProjectId(), "Reservar mesa", "comensal",
                "reservar una mesa en línea", "no llamar por teléfono", Priority.HIGH, 3);
    }

    @Test
    @DisplayName("Given a story generated in a session, when asked for its sources, then it returns the fragments with the moment of the meeting and the speaker")
    void should_return_fragments_of_the_session_with_their_moment() {
        DiscoverySession session = DiscoverySessionMother.draft().withTitle("Kickoff").build();
        UserStory story = generatedIn(session);
        story.recordOrigin(session.getId(), 2,
                "el comensal pueda reservar una mesa en línea y pagar con tarjeta", null);
        when(stories.findByIdAndProjectId(story.getId(), session.getProjectId())).thenReturn(Optional.of(story));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        TranscriptSegment first = new TranscriptSegment(session.getId(), 2, "0",
                "Necesitamos que el comensal pueda reservar una mesa en línea", 5_000, 9_000, true);
        TranscriptSegment second = new TranscriptSegment(session.getId(), 3, "0",
                "y pagar con tarjeta.", 9_000, 11_000, true);
        TranscriptSegment next = new TranscriptSegment(session.getId(), 4, "1", "Perfecto.", 11_000, 12_000, true);
        when(segments.findFinalBySessionIdBetween(session.getId(), 2, 5)).thenReturn(List.of(first, second, next));
        SessionSpeaker named = new SessionSpeaker(session.getId(), "0");
        named.describe("Ana Torres", null);
        when(speakers.rosterOf(session.getId())).thenReturn(SpeakerRoster.of(
                List.of(new SpeakerSpan("0", 5_000, 9_000), new SpeakerSpan("0", 9_000, 11_000)), List.of(named)));

        StorySources sources = handler.handle(new GetStorySourcesQuery(session.getProjectId(), story.getId()));

        assertThat(sources.origin()).isEqualTo(StorySources.Origin.SESSION);
        assertThat(sources.fragments()).singleElement().satisfies(f -> {
            assertThat(f.source().getQuote()).contains("reservar una mesa en línea y pagar con tarjeta");
            assertThat(f.session()).isSameAs(session);
            assertThat(f.segments()).containsExactly(first, second);
            assertThat(f.speakerName()).isEqualTo("Ana Torres");
        });
    }

    @Test
    @DisplayName("Given a story created manually, when asked for its sources, then it says it does not come from a session")
    void should_say_a_manual_story_has_no_session() {
        UserStory story = UserStoryMother.draft().build();
        when(stories.findByIdAndProjectId(story.getId(), story.getProjectId())).thenReturn(Optional.of(story));

        StorySources sources = handler.handle(new GetStorySourcesQuery(story.getProjectId(), story.getId()));

        assertThat(sources.origin()).isEqualTo(StorySources.Origin.MANUAL);
        assertThat(sources.sessionId()).isNull();
        assertThat(sources.fragments()).isEmpty();
        verify(segments, never()).findFinalBySessionIdBetween(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Given a story generated before fragments were kept, then the origin is unavailable but the session is reported")
    void should_report_the_session_of_a_historical_story_without_fragments() {
        DiscoverySession session = DiscoverySessionMother.draft().withTitle("Kickoff").build();
        UserStory story = generatedIn(session);
        when(stories.findByIdAndProjectId(story.getId(), session.getProjectId())).thenReturn(Optional.of(story));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        StorySources sources = handler.handle(new GetStorySourcesQuery(session.getProjectId(), story.getId()));

        assertThat(sources.origin()).isEqualTo(StorySources.Origin.UNAVAILABLE);
        assertThat(sources.sessionId()).isEqualTo(session.getId());
        assertThat(sources.session()).isSameAs(session);
        assertThat(sources.fragments()).isEmpty();
    }

    @Test
    @DisplayName("should still report the quote when its segment is gone or was never located")
    void should_keep_the_quote_when_the_segment_cannot_be_found() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UserStory story = generatedIn(session);
        story.recordOrigin(session.getId(), 7, "una cita que ya no está en la transcripción", null);
        story.addSource(session.getId(), null, "otra cita que no se ubicó");
        when(stories.findByIdAndProjectId(story.getId(), session.getProjectId())).thenReturn(Optional.of(story));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(segments.findFinalBySessionIdBetween(session.getId(), 7, 10)).thenReturn(List.of());

        StorySources sources = handler.handle(new GetStorySourcesQuery(session.getProjectId(), story.getId()));

        assertThat(sources.origin()).isEqualTo(StorySources.Origin.SESSION);
        assertThat(sources.fragments()).hasSize(2)
                .allSatisfy(f -> {
                    assertThat(f.segments()).isEmpty();
                    assertThat(f.speakerName()).isNull();
                });
    }

    @Test
    @DisplayName("should throw 404 when the story does not exist in the project")
    void should_throw_not_found() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findByIdAndProjectId(storyId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetStorySourcesQuery(projectId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
    }
}
