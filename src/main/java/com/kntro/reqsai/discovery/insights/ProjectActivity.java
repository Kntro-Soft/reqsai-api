package com.kntro.reqsai.discovery.insights;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * How much happened in one project.
 *
 * @param projectId
 *            the project
 * @param stories
 *            user stories ever created in it
 * @param sessions
 *            discovery sessions ever created in it
 * @param lastActivityAt
 *            when the newest story or session was created, null when it has
 *            none
 */
public record ProjectActivity(UUID projectId, long stories, long sessions, @Nullable Instant lastActivityAt) {
}
