package com.kntro.reqsai.workspace.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain: BasePermission floor")
class BasePermissionTest {

    @Test
    @DisplayName("NONE grants nothing")
    void none_grants_nothing() {
        assertThat(BasePermission.NONE.grantedPermissions()).isEmpty();
    }

    @Test
    @DisplayName("READ grants exactly the workspace *_READ baseline (integration read excluded)")
    void read_grants_read_baseline() {
        assertThat(BasePermission.READ.grantedPermissions()).containsExactlyInAnyOrder(
                Permission.MEMBER_READ,
                Permission.ROLE_READ,
                Permission.DOCUMENT_READ,
                Permission.GLOSSARY_READ,
                Permission.CONSTRAINT_READ,
                Permission.SESSION_READ,
                Permission.STORY_READ);
    }

    @Test
    @DisplayName("READ grants no write, manage or integration permission")
    void read_excludes_writes_and_integration() {
        Set<Permission> granted = BasePermission.READ.grantedPermissions();
        assertThat(granted)
                .doesNotContain(
                        Permission.MEMBER_INVITE,
                        Permission.ROLE_CREATE,
                        Permission.DOCUMENT_CREATE,
                        Permission.STORY_WRITE,
                        Permission.SESSION_RUN,
                        Permission.INTEGRATION_READ);
    }
}
