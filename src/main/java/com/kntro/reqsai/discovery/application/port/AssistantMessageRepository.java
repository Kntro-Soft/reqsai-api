package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.AssistantMessage;

import java.util.List;
import java.util.UUID;

/** Persistence of a project's assistant chat ({@link AssistantMessage}). */
public interface AssistantMessageRepository {

    AssistantMessage save(AssistantMessage message);

    /** The project's newest {@code limit} messages, returned oldest first (chat order). */
    List<AssistantMessage> findLatestByProjectId(UUID projectId, int limit);
}
