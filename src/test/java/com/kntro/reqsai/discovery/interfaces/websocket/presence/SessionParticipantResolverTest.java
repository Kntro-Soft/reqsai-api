package com.kntro.reqsai.discovery.interfaces.websocket.presence;

import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionParticipant;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Presence: participant resolver")
@ExtendWith(MockitoExtension.class)
class SessionParticipantResolverTest {

    @Mock
    private WorkspaceModuleApi workspace;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("resolves the member name and a deterministic avatar url")
    void resolvesNameAndAvatar() {
        var resolver = new SessionParticipantResolver(workspace);
        when(workspace.findMemberDisplayName(orgId, userId)).thenReturn(Optional.of("Ana Torres"));

        SessionParticipant participant = resolver.resolve(orgId, userId);

        assertThat(participant.userId()).isEqualTo(userId);
        assertThat(participant.displayName()).isEqualTo("Ana Torres");
        assertThat(participant.avatarUrl()).isEqualTo("/api/users/" + userId + "/avatar");
    }

    @Test
    @DisplayName("caches the display name so repeated resolves hit the workspace once")
    void cachesDisplayName() {
        var resolver = new SessionParticipantResolver(workspace);
        when(workspace.findMemberDisplayName(orgId, userId)).thenReturn(Optional.of("Ana Torres"));

        resolver.resolve(orgId, userId);
        resolver.resolve(orgId, userId);
        resolver.resolve(orgId, userId);

        verify(workspace, times(1)).findMemberDisplayName(orgId, userId);
    }

    @Test
    @DisplayName("falls back to a generic label when the membership cannot be resolved")
    void fallsBackWhenUnknown() {
        var resolver = new SessionParticipantResolver(workspace);
        when(workspace.findMemberDisplayName(orgId, userId)).thenReturn(Optional.empty());

        SessionParticipant participant = resolver.resolve(orgId, userId);

        assertThat(participant.displayName()).isEqualTo(SessionParticipantResolver.UNKNOWN_DISPLAY_NAME);
        assertThat(participant.avatarUrl()).isEqualTo("/api/users/" + userId + "/avatar");
    }
}
