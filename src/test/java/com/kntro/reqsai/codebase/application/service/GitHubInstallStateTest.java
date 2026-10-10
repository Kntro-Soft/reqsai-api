package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.domain.exception.CodebaseError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GitHubInstallState")
class GitHubInstallStateTest {

    private final GitHubInstallState state = new GitHubInstallState("client-secret");
    private final UUID organization = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("a state verifies for the organization and user it was issued for")
    void roundTrip() {
        String issued = state.issue(organization, user);
        assertThatCode(() -> state.verify(issued, organization, user)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("another organization or user, a tampered state, another server's state and none are refused")
    void refused() {
        String issued = state.issue(organization, user);
        assertInvalid(() -> state.verify(issued, UUID.randomUUID(), user));
        assertInvalid(() -> state.verify(issued, organization, UUID.randomUUID()));
        assertInvalid(() -> state.verify(issued.replace('.', 'x') + ".AA", organization, user));
        assertInvalid(() -> new GitHubInstallState("other-secret").verify(issued, organization, user));
        assertInvalid(() -> state.verify(null, organization, user));
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.error())
                        .isEqualTo(CodebaseError.CODE_HOST_INSTALL_STATE_INVALID));
    }
}
