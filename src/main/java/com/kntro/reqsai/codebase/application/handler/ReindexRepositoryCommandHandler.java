package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.ReindexRepositoryCommand;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.service.CodeIndexLauncher;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Starts a new indexing run unless one is running (a run that stalled can be restarted). */
@Component
@RequiredArgsConstructor
public class ReindexRepositoryCommandHandler {

    private final CodeRepositoryRepository repositories;
    private final CodeIndexLauncher launcher;

    @Transactional
    public CodeRepository handle(ReindexRepositoryCommand command) {
        CodeRepository repository = repositories.findByIdAndProjectId(command.repositoryId(), command.projectId())
                .orElseThrow(() -> CodebaseExceptions.notFound(command.repositoryId().toString()));
        Instant now = Instant.now();
        if (repository.getStatus().isRunning() && !repository.isStalled(now, CodeRepository.STALL_AFTER)) {
            throw CodebaseExceptions.indexing();
        }
        repository.requestIndexing(now);
        CodeRepository saved = repositories.save(repository);
        launcher.launch(saved.getId());
        return saved;
    }
}
