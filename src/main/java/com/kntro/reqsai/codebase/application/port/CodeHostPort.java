package com.kntro.reqsai.codebase.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reads repositories from a code host (GitHub today). Implementations translate host failures into
 * {@code CodebaseExceptions}: unknown repository or branch → not found, rejected token → access denied,
 * outage or rate limit → host unavailable. The token, when given, is never logged.
 */
public interface CodeHostPort {

    /** The repository's metadata; fails with not found for a private repository read without a token. */
    RemoteRepository describe(String owner, String name, @Nullable String token);

    /** The commit the branch points at. */
    String headCommit(String owner, String name, String branch, @Nullable String token);

    /**
     * The text files of the repository at {@code ref} whose path {@code keep} accepts, within the
     * {@code limits}. Paths are relative to the repository root ({@code src/app/main.ts}).
     */
    RepositoryArchive download(String owner, String name, String ref, @Nullable String token,
                               ArchiveLimits limits, Predicate<String> keep);

    record RemoteRepository(String owner, String name, String defaultBranch, String htmlUrl, boolean isPrivate) {
    }

    /** How much of an archive is read: compressed download size, files kept and size of each file. */
    record ArchiveLimits(long maxDownloadBytes, int maxFiles, long maxFileBytes) {
    }

    /** {@code truncated} when {@code maxFiles} was reached and the rest of the archive was skipped. */
    record RepositoryArchive(List<SourceFile> files, int skippedFiles, boolean truncated) {
    }

    record SourceFile(String path, String content) {
    }
}
