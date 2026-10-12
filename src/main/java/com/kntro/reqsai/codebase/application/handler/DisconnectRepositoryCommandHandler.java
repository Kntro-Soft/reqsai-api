package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.DisconnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes a repository and its modules; a run still in flight finds it gone and stops writing. */
@Component
@RequiredArgsConstructor
public class DisconnectRepositoryCommandHandler {

    private final CodeRepositoryRepository repositories;
    private final CodeModuleRepository modules;

    @Transactional
    public void handle(DisconnectRepositoryCommand command) {
        CodeRepository repository = repositories.findByIdAndProjectId(command.repositoryId(), command.projectId())
                .orElseThrow(() -> CodebaseExceptions.notFound(command.repositoryId().toString()));
        modules.deleteByRepositoryId(repository.getId());
        repositories.delete(repository);
    }
}
