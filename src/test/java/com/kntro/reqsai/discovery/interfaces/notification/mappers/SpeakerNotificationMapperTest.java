package com.kntro.reqsai.discovery.interfaces.notification.mappers;

import com.kntro.reqsai.discovery.domain.event.SessionSpeakerUpdatedEvent;
import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionSpeakerUpdatedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification mapper: SpeakerNotificationMapper")
class SpeakerNotificationMapperTest {

    @Test
    @DisplayName("maps the speaker's new description to a SPEAKER_UPDATED message")
    void maps_event() {
        UUID sessionId = UUID.randomUUID();
        SessionSpeakerUpdatedEvent event = SessionSpeakerUpdatedEvent.of(sessionId, "1", "Luis", SpeakerSide.TEAM);

        SessionSpeakerUpdatedMessage message = SpeakerNotificationMapper.toMessage(event);

        assertThat(message.type()).isEqualTo(SessionEventType.SPEAKER_UPDATED);
        assertThat(message.sessionId()).isEqualTo(sessionId);
        assertThat(message.speakerLabel()).isEqualTo("1");
        assertThat(message.displayName()).isEqualTo("Luis");
        assertThat(message.side()).isEqualTo("TEAM");
        assertThat(message.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(SpeakerNotificationMapper.toMessage(SessionSpeakerUpdatedEvent.of(sessionId, "1", null, null)).side())
                .isNull();
    }
}
