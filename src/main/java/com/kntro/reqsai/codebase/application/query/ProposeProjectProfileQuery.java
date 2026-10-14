package com.kntro.reqsai.codebase.application.query;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The sources a project that does not exist yet was pointed at.
 *
 * @param repositories the GitHub repositories picked, at most a few
 * @param jiraName     the picked Jira project's name, if any
 * @param jiraDescription its description, if any
 * @param language     the language to write the proposal in (a BCP-47 tag or a bare language code)
 */
public record ProposeProjectProfileQuery(List<Repository> repositories, @Nullable String jiraName,
                                         @Nullable String jiraDescription, String language) {

    /** A repository of an installation of the organization. */
    public record Repository(long installationId, String fullName) {
    }
}
