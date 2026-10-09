package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.model.CodeModule;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The short transactions of an indexing run. The run itself holds no transaction (it waits on GitHub and
 * the AI for minutes); every state change and module is written here on its own, so progress is visible
 * while the run goes on. A repository removed mid-run makes these calls no-ops.
 */
@Component
@RequiredArgsConstructor
public class CodeIndexWriter {

    private final CodeRepositoryRepository repositories;
    private final CodeModuleRepository modules;

    /** What a run needs to read the repository. */
    public record RunTarget(UUID repositoryId, UUID projectId, String owner, String name, String branch,
                            byte @Nullable [] tokenCiphertext) {
    }

    public record ModuleState(String contentHash, boolean summarized, String name, String summary) {
    }

    @Transactional
    public @Nullable RunTarget start(UUID repositoryId) {
        CodeRepository repo = repositories.findById(repositoryId).orElse(null);
        if (repo == null) return null;
        repo.startIndexing(Instant.now());
        repositories.save(repo);
        return new RunTarget(repo.getId(), repo.getProjectId(), repo.getOwner(), repo.getName(), repo.getBranch(),
                repo.getAccessTokenCiphertext());
    }

    @Transactional
    public void recordStructure(UUID repositoryId, String commitSha, int fileCount, int moduleCount,
                                CodeProfile profile) {
        repositories.findById(repositoryId).ifPresent(repo -> {
            repo.recordStructure(commitSha, fileCount, moduleCount, profile, Instant.now());
            repositories.save(repo);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, ModuleState> existingModules(UUID repositoryId) {
        Map<String, ModuleState> states = new HashMap<>();
        modules.findAllByRepositoryId(repositoryId)
                .forEach(m -> states.put(m.getPath(), new ModuleState(m.getContentHash(), m.isSummarized(), m.getName(), m.getSummary())));
        return states;
    }

    @Transactional
    public void saveModule(UUID repositoryId, UUID projectId, String path, String name, String summary,
                           List<String> capabilities, List<String> businessRules, List<String> endpoints,
                           List<String> entities, int fileCount, String contentHash, boolean summarized,
                           float @Nullable [] embedding) {
        if (repositories.findById(repositoryId).isEmpty()) return;
        CodeModule module = modules.findByRepositoryIdAndPath(repositoryId, path)
                .orElseGet(() -> new CodeModule(repositoryId, projectId, path));
        module.describe(name, summary, capabilities, businessRules, endpoints, entities, fileCount, contentHash,
                summarized, embedding);
        modules.save(module);
    }

    @Transactional
    public void progress(UUID repositoryId, int modulesDone) {
        repositories.findById(repositoryId).ifPresent(repo -> {
            repo.recordProgress(modulesDone, Instant.now());
            repositories.save(repo);
        });
    }

    @Transactional
    public void keepOnly(UUID repositoryId, Collection<String> paths) {
        if (repositories.findById(repositoryId).isEmpty()) return;
        modules.deleteByRepositoryIdAndPathNotIn(repositoryId, paths);
    }

    @Transactional
    public void markReady(UUID repositoryId, boolean summarized, CodeProfile profile) {
        repositories.findById(repositoryId).ifPresent(repo -> {
            repo.markReady(summarized, profile, Instant.now());
            repositories.save(repo);
        });
    }

    @Transactional
    public void markFailed(UUID repositoryId, String reason) {
        repositories.findById(repositoryId).ifPresent(repo -> {
            repo.markFailed(reason, Instant.now());
            repositories.save(repo);
        });
    }
}
