package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.query.ListRepositoriesQuery;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListRepositoriesQueryHandler {

    private final CodeRepositoryRepository repositories;

    @Transactional(readOnly = true)
    public List<CodeRepository> handle(ListRepositoriesQuery query) {
        return repositories.findAllByProjectId(query.projectId());
    }
}
