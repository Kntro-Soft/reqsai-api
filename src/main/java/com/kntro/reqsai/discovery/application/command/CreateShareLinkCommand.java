package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.ShareAccess;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The analyst opens a link so a client can review the project's stories, or follow one discovery
 * session, without an account.
 *
 * @param projectId the project the link belongs to
 * @param days      how many days the link stays valid
 * @param sessionId the session the link follows; {@code null} shares the project's stories
 * @param access    what the visitor may do on a session link
 */
public record CreateShareLinkCommand(UUID projectId, int days, @Nullable UUID sessionId, ShareAccess access) {

    public CreateShareLinkCommand(UUID projectId, int days) {
        this(projectId, days, null, ShareAccess.REVIEW);
    }
}
