package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to record that the analyst confirmed the participants' consent to the recording.
 *
 * @param projectId   the project the session belongs to
 * @param sessionId   the session about to be captured
 * @param confirmedBy the user who confirms the consent
 */
public record ConfirmRecordingConsentCommand(UUID projectId, UUID sessionId, UUID confirmedBy) {
}
