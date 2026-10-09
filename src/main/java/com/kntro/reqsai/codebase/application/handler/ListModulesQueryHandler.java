package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.query.ListModulesQuery;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeModule;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListModulesQueryHandler {

    private final CodeRepositoryRepository repositories;
    private final CodeModuleRepository modules;

    public record RepositoryModules(CodeRepository repository, List<CodeModule> modules) {
    }

    @Transactional(readOnly = true)
    public RepositoryModules handle(ListModulesQuery query) {
        CodeRepository repository = repositories.findByIdAndProjectId(query.repositoryId(), query.projectId())
                .orElseThrow(() -> CodebaseExceptions.notFound(query.repositoryId().toString()));
        return new RepositoryModules(repository, modules.findAllByRepositoryId(repository.getId()));
    }
}
