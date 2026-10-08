package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.ProjectDiscoveryDataRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * JPQL bulk deletes over the project's discovery tables, children first. Acceptance criteria are removed
 * by the {@code ON DELETE CASCADE} of their {@code story_id} foreign key; client feedback on the stories
 * (which carries no foreign key) is deleted explicitly. Share links are left alone: they belong to the
 * project, not to its sample content. The persistence context is flushed
 * before and deliberately <em>not</em> cleared afterwards: the caller (the demo restore) may still hold
 * managed workspace aggregates in the same transaction.
 */
@Component
@Slf4j
class ProjectDiscoveryDataRepositoryAdapter implements ProjectDiscoveryDataRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void deleteAllByProjectId(UUID projectId) {
        entityManager.flush();
        int suggestions = delete("delete from Suggestion s where s.projectId = :projectId", projectId);
        int messages = delete("delete from AssistantMessage m where m.projectId = :projectId", projectId);
        int feedback = delete("delete from StoryFeedback f where f.storyId in "
                + "(select u.id from UserStory u where u.projectId = :projectId)", projectId);
        int stories = delete("delete from UserStory u where u.projectId = :projectId", projectId);
        int segments = delete("delete from TranscriptSegment t where t.sessionId in "
                + "(select s.id from DiscoverySession s where s.projectId = :projectId)", projectId);
        int sessions = delete("delete from DiscoverySession s where s.projectId = :projectId", projectId);
        log.info("Purged discovery data of project {}: {} sessions, {} segments, {} stories, {} client "
                + "feedback, {} suggestions, {} chat messages",
                projectId, sessions, segments, stories, feedback, suggestions, messages);
    }

    private int delete(String jpql, UUID projectId) {
        return entityManager.createQuery(jpql).setParameter("projectId", projectId).executeUpdate();
    }
}
