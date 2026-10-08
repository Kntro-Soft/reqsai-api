package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.AssistantMessageRepository;
import com.kntro.reqsai.discovery.domain.model.AssistantMessage;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.AssistantMessageJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Adapts {@link AssistantMessageRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class AssistantMessageRepositoryAdapter implements AssistantMessageRepository {

    private final AssistantMessageJpaRepository jpa;

    @Override
    public AssistantMessage save(AssistantMessage message) {
        return jpa.save(message);
    }

    @Override
    public List<AssistantMessage> findLatestByProjectId(UUID projectId, int limit) {
        List<AssistantMessage> newestFirst = new ArrayList<>(jpa.findNewestByProjectId(projectId, PageRequest.of(0, limit)));
        Collections.reverse(newestFirst);
        return newestFirst;
    }
}
