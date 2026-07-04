package com.kntro.reqsai.discovery.interfaces.rest.security;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Interfaces: DiscoveryAuthorization (@discoveryAuthz)")
@ExtendWith(MockitoExtension.class)
class DiscoveryAuthorizationTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private DiscoveryAuthorization authz;

    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    @DisplayName("should resolve the session's project and delegate the permission check")
    void should_delegate_to_workspace_with_session_project() {
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        when(authentication.getName()).thenReturn(userId.toString());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(workspace.callerHasProjectPermission(projectId, userId, "SESSION_READ")).thenReturn(true);

        assertThat(authz.sessionPermission(session.getId(), "SESSION_READ", authentication)).isTrue();
    }

    @Test
    @DisplayName("should deny when the caller lacks the permission on the session's project")
    void should_deny_without_permission() {
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        when(authentication.getName()).thenReturn(userId.toString());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(workspace.callerHasProjectPermission(projectId, userId, "SESSION_DECIDE")).thenReturn(false);

        assertThat(authz.sessionPermission(session.getId(), "SESSION_DECIDE", authentication)).isFalse();
    }

    @Test
    @DisplayName("should pass for a missing session so the handler answers 404 instead of 403")
    void should_pass_when_session_absent() {
        UUID sessionId = UUID.randomUUID();
        when(authentication.getName()).thenReturn(userId.toString());
        when(sessions.findById(sessionId)).thenReturn(Optional.empty());

        assertThat(authz.sessionPermission(sessionId, "SESSION_RUN", authentication)).isTrue();
        verifyNoInteractions(workspace);
    }

    @Test
    @DisplayName("should deny when the authentication is missing or malformed")
    void should_deny_without_caller() {
        assertThat(authz.sessionPermission(UUID.randomUUID(), "SESSION_READ", null)).isFalse();

        when(authentication.getName()).thenReturn("not-a-uuid");
        assertThat(authz.sessionPermission(UUID.randomUUID(), "SESSION_READ", authentication)).isFalse();
        verifyNoInteractions(sessions, workspace);
    }
}
