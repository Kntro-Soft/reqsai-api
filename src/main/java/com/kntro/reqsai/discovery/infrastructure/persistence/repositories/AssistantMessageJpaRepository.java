package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.AssistantMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link AssistantMessage} (tenant-scoped table {@code assistant_messages}). */
public interface AssistantMessageJpaRepository extends JpaRepository<AssistantMessage, UUID> {

    /** Newest first; ids are time-ordered, so they break ties between messages saved in the same instant. */
    @Query("select m from AssistantMessage m where m.projectId = :projectId order by m.createdAt desc, m.id desc")
    List<AssistantMessage> findNewestByProjectId(@Param("projectId") UUID projectId, Pageable pageable);
}
