package com.kntro.reqsai.workspace.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * The external sources a project asks to be wired to when it is created: a Jira project (and whether to import
 * its stories) and up to a few GitHub repositories. Plain values only, so Workspace can announce them without
 * knowing the modules that act on them.
 *
 * @param jira   the Jira project to link, or null
 * @param github the repositories to connect and index, empty for none
 */
public record ProjectSources(@Nullable Jira jira, List<GitHub> github) implements Serializable {

    public ProjectSources {
        github = github == null ? List.of() : List.copyOf(github);
    }

    public static ProjectSources none() {
        return new ProjectSources(null, List.of());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return jira == null && github.isEmpty();
    }

    /**
     * @param connectionId   the organization's Jira connection; when null, its active one
     * @param projectKey     the Jira project key
     * @param issueTypeName  the issue type stories are pushed as
     * @param importStories  whether to import the project's existing issues as stories
     */
    public record Jira(@Nullable UUID connectionId, String projectKey, String issueTypeName, boolean importStories)
            implements Serializable {
    }

    /**
     * @param repository     {@code owner/name} or a github.com URL
     * @param branch         the branch to read; the default branch when blank
     * @param installationId the organization's GitHub App installation that shares it, when picked
     */
    public record GitHub(String repository, @Nullable String branch, @Nullable Long installationId)
            implements Serializable {
    }
}
