package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.application.command.CreateProjectCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRequest;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public final class ProjectRequestMapper {

    private ProjectRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** A null/omitted list in the request means "no entries"; downstream code never sees null lists. */
    private static List<String> orEmpty(@Nullable List<String> values) {
        return values == null ? List.of() : values;
    }

    public static CreateProjectCommand toCommand(UUID orgId, CreateProjectRequest request, UUID requestedBy) {
        return new CreateProjectCommand(
                orgId,
                request.name(),
                request.description(),
                orEmpty(request.programmingLanguages()),
                orEmpty(request.frameworks()),
                orEmpty(request.clientPlatforms()),
                orEmpty(request.databases()),
                request.architecture(),
                request.domain(),
                requestedBy,
                toSources(request.sources())
        );
    }

    public static ProjectSources toSources(CreateProjectRequest.@Nullable Sources sources) {
        if (sources == null) return ProjectSources.none();
        ProjectSources.Jira jira = sources.jira() == null ? null : new ProjectSources.Jira(
                sources.jira().connectionId(), sources.jira().projectKey().strip(),
                sources.jira().issueTypeName().strip(), sources.jira().importStories());
        List<ProjectSources.GitHub> github = sources.github() == null ? List.of() : sources.github().stream()
                .map(repo -> new ProjectSources.GitHub(repo.repository().strip(), repo.branch(), repo.installationId()))
                .toList();
        return new ProjectSources(jira, github);
    }

    public static UpdateProjectCommand toCommand(UUID orgId, UUID projectId, UpdateProjectRequest request, UUID requestedBy) {
        return new UpdateProjectCommand(
                orgId,
                projectId,
                request.name(),
                request.description(),
                orEmpty(request.programmingLanguages()),
                orEmpty(request.frameworks()),
                orEmpty(request.clientPlatforms()),
                orEmpty(request.databases()),
                request.architecture(),
                request.domain(),
                requestedBy
        );
    }
}
