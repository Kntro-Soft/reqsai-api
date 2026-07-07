package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StartSttStreamCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a live STT stream for a discovery session, enforcing that the session is in
 * {@code RECORDING} status before connecting to the upstream STT provider.
 *
 * <p>This handler is the application-layer gatekeeper for the WebSocket audio channel:
 * the WS handler calls it once per connection to obtain a
 * {@link StreamingTranscriptionPort.Session} and passes the transcript listener as a callback.
 * The handler does not own the listener — it belongs to the caller (the WS adapter).
 *
 * <p>Throws {@link DomainException} with {@link DiscoveryError#SESSION_ACCESS_DENIED} if the
 * caller lacks the {@code SESSION_RUN} permission on the session's project (org owners/admins
 * bypass), or {@link DiscoveryError#INVALID_SESSION_STATUS} if the session is not in
 * {@code RECORDING}; the WS handler catches these and closes the connection with
 * {@code POLICY_VIOLATION}.
 */
@Component
@RequiredArgsConstructor
public class StartSttStreamCommandHandler {

    /** Permission gated on the STT stream — recording audio is part of running a session. */
    static final String REQUIRED_PERMISSION = "SESSION_RUN";

    private final DiscoverySessionRepository sessions;
    private final StreamingTranscriptionPort streaming;
    private final WorkspaceModuleApi workspace;

    @Transactional(readOnly = true)
    public StreamingTranscriptionPort.Session handle(StartSttStreamCommand command, StreamingTranscriptionPort.Listener listener) {

        var session = sessions.findById(command.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        if (!workspace.callerHasProjectPermission(session.getProjectId(), command.userId(), REQUIRED_PERMISSION)) {
            throw DiscoveryExceptions.sessionAccessDenied(command.sessionId(), command.userId());
        }

        if (session.getStatus() != SessionStatus.RECORDING) {
            throw new DomainException(DiscoveryError.INVALID_SESSION_STATUS, "Session %s must be in RECORDING status to open an STT stream (current: %s)".formatted(command.sessionId(), session.getStatus()));
        }

        String language = session.getLanguage() != null ? session.getLanguage().value() : null;
        return streaming.open(new StreamingTranscriptionPort.Context(command.sessionId(), language), listener);
    }
}
