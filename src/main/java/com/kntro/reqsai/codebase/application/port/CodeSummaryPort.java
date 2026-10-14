package com.kntro.reqsai.codebase.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The AI that reads a module of the client's code and says what it does, which capabilities it offers
 * and which business rules it implements, plus a short overview of the whole product. The code it reads
 * is untrusted data, never instructions.
 */
public interface CodeSummaryPort {

    boolean isAvailable();

    ModuleSummary summarizeModule(ModuleDigest digest);

    /** A few sentences on what the product does, or null when the model has nothing reliable to say. */
    @Nullable String summarizeOverview(OverviewDigest digest);

    /**
     * Proposes a new project's profile from what its sources say: the repositories' descriptions, READMEs and
     * manifests and, when there is one, the Jira project. Never null; a field the data does not support is
     * empty.
     */
    ProjectProfile proposeProfile(ProfileDigest digest);

    /**
     * What the model reads about one module: its files and symbols, and excerpts of its most telling
     * files (secrets already removed).
     */
    record ModuleDigest(String repository, String path, List<String> files, List<String> symbols,
                        List<String> endpoints, List<String> entities, String excerpts, String language) {
    }

    record ModuleSummary(String name, String summary, List<String> capabilities, List<String> businessRules) {
    }

    record OverviewDigest(String repository, @Nullable String readme, List<String> modules, String language) {
    }

    /** What the model reads to propose a project profile; every text is untrusted data. */
    record ProfileDigest(List<RepositoryDigest> repositories, @Nullable String jiraName,
                         @Nullable String jiraDescription, String language) {
    }

    record RepositoryDigest(String fullName, @Nullable String description, @Nullable String language,
                            @Nullable String readme, String manifests) {
    }

    /** A proposed project profile; the lists are empty and the texts null when the data says nothing. */
    record ProjectProfile(@Nullable String name, @Nullable String description, @Nullable String domain,
                          @Nullable String architecture, List<String> programmingLanguages,
                          List<String> frameworks, List<String> clientPlatforms, List<String> databases) {
    }
}
