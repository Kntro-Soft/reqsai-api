package com.kntro.reqsai.workspace.api;

import org.jspecify.annotations.Nullable;

/**
 * One step as a source module sees it from the state it owns (the Jira target and job, the repositories).
 *
 * @param key    a {@link SetupStepKeys} value
 * @param status where the step stands
 * @param done   items finished so far (modules indexed, issues imported)
 * @param total  items to do; 0 while unknown
 * @param detail a short note (the reason of a failure, a count), or null
 */
public record SetupStepView(String key, SetupStepStatus status, int done, int total, @Nullable String detail) {
}
