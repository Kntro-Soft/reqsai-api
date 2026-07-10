package com.kntro.reqsai.gateway.interfaces.notification.mappers;

import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.interfaces.notification.messages.IntegrationJobMessage;

/** Maps an {@link IntegrationSyncJob} snapshot to its realtime {@link IntegrationJobMessage}. */
public final class IntegrationJobNotificationMapper {

    private IntegrationJobNotificationMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static IntegrationJobMessage toMessage(IntegrationSyncJob job) {
        return new IntegrationJobMessage(
                job.getId(),
                job.getProjectId(),
                job.getJobType(),
                job.getStatus(),
                job.getTotal(),
                job.getProcessed(),
                job.getSucceeded(),
                job.getFailed(),
                job.getMessage(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}
