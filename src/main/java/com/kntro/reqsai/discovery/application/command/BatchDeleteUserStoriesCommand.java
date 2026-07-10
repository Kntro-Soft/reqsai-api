package com.kntro.reqsai.discovery.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Intent to permanently delete several user stories of a project in one call. Ids that do not belong
 * to {@code projectId} (unknown, or in another project/tenant) are silently skipped — the operation is
 * best-effort and reports how many rows were actually deleted, never an error for a missing id. Each
 * deleted story's acceptance criteria are removed with it (JPA cascade / orphan removal).
 *
 * @param projectId project the stories must belong to
 * @param storyIds  candidate stories to delete (order preserved; ids not in the project are skipped)
 */
public record BatchDeleteUserStoriesCommand(UUID projectId, List<UUID> storyIds) {}
