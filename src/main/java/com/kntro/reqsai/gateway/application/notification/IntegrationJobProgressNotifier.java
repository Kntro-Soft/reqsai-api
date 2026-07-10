package com.kntro.reqsai.gateway.application.notification;

import com.kntro.reqsai.gateway.domain.model.IntegrationSyncJob;
import com.kntro.reqsai.gateway.interfaces.notification.mappers.IntegrationJobNotificationMapper;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publishes a job's current state to its project topic ({@link IntegrationJobTopics#jobsOf}) after
 * every persisted update. The worker publishes per item — at the current batch scale (tens of
 * issues) no throttling is needed; the durable row remains the source of truth if a frame is lost
 * (the shared {@link RealtimeNotifier} never propagates send failures).
 */
@Component
@RequiredArgsConstructor
public class IntegrationJobProgressNotifier {

    private final RealtimeNotifier notifier;

    public void publish(IntegrationSyncJob job) {
        notifier.broadcast(IntegrationJobTopics.jobsOf(job.getProjectId()),
                IntegrationJobNotificationMapper.toMessage(job));
    }
}
