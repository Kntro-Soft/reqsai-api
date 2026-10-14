package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.ArchiveLimits;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.ProfileDigest;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.ProjectProfile;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.RepositoryDigest;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort.AppRepository;
import com.kntro.reqsai.codebase.application.query.ProposeProjectProfileQuery;
import com.kntro.reqsai.codebase.application.result.ProjectProfileProposal;
import com.kntro.reqsai.codebase.application.service.RepositoryAccess;
import com.kntro.reqsai.codebase.application.service.SourceFilter;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Proposes a new project's name, description and technical profile from the Jira project and GitHub
 * repositories it was pointed at (the create-project wizard). For each repository it reads the README and the
 * manifests (the repository's own text files, nothing else) through the organization's GitHub App, then asks
 * the model once for a profile covering all the sources. Needs the AI allowance. When no model is available,
 * or the model's answer cannot be read, it falls back to what the sources' metadata says, so the wizard always
 * has something to fill.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProposeProjectProfileQueryHandler {

    /** Repositories read per proposal. */
    static final int MAX_REPOSITORIES = 3;
    static final int MAX_README_CHARS = 4_000;
    static final int MAX_MANIFEST_CHARS = 1_500;
    static final int MAX_MANIFESTS = 6;
    private static final int MAX_DEPTH = 3;

    private final RepositoryAccess access;
    private final GitHubAppPort app;
    private final CodeHostPort host;
    private final CodeSummaryPort summary;
    private final AiQuotaGuard quota;

    @Value("${reqsai.codebase.max-download-bytes:157286400}")
    private long maxDownloadBytes = 157_286_400L;

    public ProjectProfileProposal handle(ProposeProjectProfileQuery query) {
        quota.assertAvailable();
        List<RepositoryDigest> digests = new ArrayList<>();
        for (ProposeProjectProfileQuery.Repository repository : query.repositories().stream().limit(MAX_REPOSITORIES).toList()) {
            digests.add(read(repository));
        }
        ProfileDigest digest = new ProfileDigest(digests, blankToNull(query.jiraName()),
                blankToNull(query.jiraDescription()), languageName(query.language()));
        if (!summary.isAvailable()) {
            return fallback(digest);
        }
        try {
            ProjectProfile profile = AiUsageScope.call(AiFeature.PROJECT_SETUP, null, () -> summary.proposeProfile(digest));
            return new ProjectProfileProposal(true, profile.name(), profile.description(), profile.domain(),
                    profile.architecture(), profile.programmingLanguages(), profile.frameworks(),
                    profile.clientPlatforms(), profile.databases());
        } catch (IllegalStateException e) {
            log.info("Project profile: the model's reply was unusable, answering from the metadata: {}", e.getMessage());
            return fallback(digest);
        }
    }

    /** One repository's metadata, README and manifests; a repository that cannot be read is described by its metadata. */
    private RepositoryDigest read(ProposeProjectProfileQuery.Repository repository) {
        String[] parts = repository.fullName().split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw CodebaseExceptions.urlInvalid(repository.fullName());
        }
        String owner = parts[0];
        String name = parts[1];
        long installationId = repository.installationId();
        access.linked(installationId);
        AppRepository meta = app.repositories(installationId).stream()
                .filter(r -> r.owner().equalsIgnoreCase(owner) && r.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> CodebaseExceptions.notFound(repository.fullName()));
        String readme = null;
        StringBuilder manifests = new StringBuilder();
        try {
            String token = access.tokenFor(installationId);
            String head = host.headCommit(meta.owner(), meta.name(), meta.defaultBranch(), token);
            var archive = host.download(meta.owner(), meta.name(), head, token,
                    new ArchiveLimits(maxDownloadBytes, 400, 60_000), ProposeProjectProfileQueryHandler::wanted);
            List<SourceFile> files = archive.files();
            readme = files.stream()
                    .filter(f -> SourceFilter.isReadme(f.path()))
                    .min(Comparator.comparingInt((SourceFile f) -> depth(f.path())).thenComparing(SourceFile::path))
                    .map(f -> cut(f.content(), MAX_README_CHARS))
                    .orElse(null);
            Stream<SourceFile> manifestFiles = files.stream()
                    .filter(f -> SourceFilter.isManifest(f.path()))
                    .sorted(Comparator.comparingInt((SourceFile f) -> depth(f.path())).thenComparing(SourceFile::path))
                    .limit(MAX_MANIFESTS);
            manifestFiles.forEach(f -> manifests.append("--- ").append(f.path()).append('\n')
                    .append(cut(f.content(), MAX_MANIFEST_CHARS)).append('\n'));
        } catch (RuntimeException e) {
            log.info("Project profile: could not read {} ({}); using its metadata only", repository.fullName(), e.getMessage());
        }
        return new RepositoryDigest(meta.owner() + "/" + meta.name(), meta.description(), meta.language(), readme,
                manifests.toString());
    }

    /** Only the README and the manifests near the root: the profile needs the stack, not the code. */
    private static boolean wanted(String path) {
        return depth(path) <= MAX_DEPTH && (SourceFilter.isReadme(path) || SourceFilter.isManifest(path));
    }

    private static int depth(String path) {
        int count = 0;
        for (char c : path.toCharArray()) {
            if (c == '/') count++;
        }
        return count;
    }

    /** What the sources' metadata alone says: the first repository leads, the languages are all of them. */
    private static ProjectProfileProposal fallback(ProfileDigest digest) {
        RepositoryDigest first = digest.repositories().isEmpty() ? null : digest.repositories().get(0);
        List<String> languages = digest.repositories().stream()
                .map(RepositoryDigest::language)
                .filter(l -> l != null && !l.isBlank())
                .distinct()
                .toList();
        String name = digest.jiraName() != null ? digest.jiraName() : first == null ? null : first.fullName().substring(first.fullName().indexOf('/') + 1);
        String description = digest.jiraDescription() != null ? digest.jiraDescription() : first == null ? null : first.description();
        return new ProjectProfileProposal(false, name, description, null, null, languages, List.of(), List.of(), List.of());
    }

    private static String cut(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max) + "\n…";
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** The language name the prompt asks the answer in ("es-PE" becomes "Spanish"). */
    static String languageName(@Nullable String tag) {
        String code = tag == null ? "" : tag.strip().toLowerCase(Locale.ROOT);
        int dash = code.indexOf('-');
        if (dash > 0) code = code.substring(0, dash);
        return switch (code) {
            case "en" -> "English";
            case "pt" -> "Portuguese";
            case "fr" -> "French";
            case "de" -> "German";
            case "it" -> "Italian";
            default -> "Spanish";
        };
    }
}
