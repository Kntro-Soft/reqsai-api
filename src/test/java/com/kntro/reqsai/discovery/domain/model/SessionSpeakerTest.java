package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.SessionSpeakerUpdatedEvent;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@link SessionSpeaker} aggregate (US40). */
@DisplayName("Domain: SessionSpeaker")
class SessionSpeakerTest {

    private static final UUID SESSION = UUID.randomUUID();

    @Test
    @DisplayName("starts undescribed: no name, no side")
    void starts_undescribed() {
        SessionSpeaker speaker = new SessionSpeaker(SESSION, " 0 ");

        assertThat(speaker.getSessionId()).isEqualTo(SESSION);
        assertThat(speaker.getSpeakerLabel()).isEqualTo("0");
        assertThat(speaker.getDisplayName()).isNull();
        assertThat(speaker.getSide()).isNull();
    }

    @Test
    @DisplayName("stores the name on one line, trimmed, with its side, and raises SessionSpeakerUpdatedEvent")
    void describe_names_and_sides_the_speaker() {
        SessionSpeaker speaker = new SessionSpeaker(SESSION, "1");

        speaker.describe("  Ana\n  Torres\t", SpeakerSide.CLIENT);

        assertThat(speaker.getDisplayName()).isEqualTo("Ana Torres");
        assertThat(speaker.getSide()).isEqualTo(SpeakerSide.CLIENT);
        assertThat(AggregateEvents.of(speaker)).hasSize(1).first().isInstanceOf(SessionSpeakerUpdatedEvent.class);
        SessionSpeakerUpdatedEvent event = (SessionSpeakerUpdatedEvent) AggregateEvents.of(speaker).getFirst();
        assertThat(event.sessionId()).isEqualTo(SESSION);
        assertThat(event.speakerLabel()).isEqualTo("1");
        assertThat(event.displayName()).isEqualTo("Ana Torres");
        assertThat(event.side()).isEqualTo(SpeakerSide.CLIENT);
    }

    @Test
    @DisplayName("a blank name goes back to the default and a null side unsets it")
    void blank_name_resets_to_default() {
        SessionSpeaker speaker = new SessionSpeaker(SESSION, "1");
        speaker.describe("Ana", SpeakerSide.TEAM);

        speaker.describe("   ", null);

        assertThat(speaker.getDisplayName()).isNull();
        assertThat(speaker.getSide()).isNull();
    }

    @Test
    @DisplayName("rejects a blank label and a name longer than 80 characters")
    void rejects_invalid_input() {
        assertThatThrownBy(() -> new SessionSpeaker(SESSION, " ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new SessionSpeaker(null, "0")).isInstanceOf(DomainException.class);
        SessionSpeaker speaker = new SessionSpeaker(SESSION, "0");
        assertThatThrownBy(() -> speaker.describe("x".repeat(SessionSpeaker.NAME_MAX + 1), null))
                .isInstanceOf(DomainException.class);
    }
}
