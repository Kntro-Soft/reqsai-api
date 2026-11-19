package com.kntro.reqsai.discovery.infrastructure.scheduler;

import com.kntro.reqsai.discovery.application.service.PurgeExpiredAudiosService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled "purga de audios vencidos" (US69). Runs only when
 * {@code reqsai.discovery.audio-retention.enabled=true}; the cron comes from
 * {@code reqsai.discovery.audio-retention.cron} (UTC, daily at 03:30 by
 * default). All the work, including the per-tenant context handling, lives in
 * {@link PurgeExpiredAudiosService}.
 */
@Component
@ConditionalOnProperty(prefix = "reqsai.discovery.audio-retention", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class AudioRetentionPurgeScheduler {

    private final PurgeExpiredAudiosService purge;

    @Scheduled(cron = "${reqsai.discovery.audio-retention.cron:0 30 3 * * *}", zone = "UTC")
    void purgeExpiredAudios() {
        purge.purgeAllTenants();
    }
}
