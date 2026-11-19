package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.command.PurgeExpiredAudiosCommand;
import com.kntro.reqsai.discovery.application.config.AudioRetentionProperties;
import com.kntro.reqsai.discovery.application.handler.PurgeExpiredAudiosCommandHandler;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.api.AudioRetentionPolicy;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Purge of expired audios (US69): visits every tenant and deletes the audios
 * that outlived the retention period of their organization (US15).
 *
 * <p>
 * A scheduled run has no request, hence no bound tenant: the service reads each
 * organization's policy through {@link WorkspaceModuleApi} and runs the purge
 * of that tenant inside {@link TenantContext#runWith}, so every query lands in
 * the right {@code tenant_<slug>} schema and the context is always cleared
 * afterwards. A failure in one tenant is logged and skipped so it never blocks
 * the others; organizations that keep their audios forever are not touched.
 * Idempotent: purged audios are no longer candidates, so running it twice (or
 * from two instances at once) deletes each audio once. Logs only counts and
 * tenant ids, never session titles, file names or any user data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurgeExpiredAudiosService {

    private final WorkspaceModuleApi workspace;
    private final PurgeExpiredAudiosCommandHandler purgeBatch;
    private final AudioRetentionProperties properties;

    /**
     * Result of one run: audios deleted, tenants visited, skipped (keep forever)
     * and failed.
     */
    public record Summary(int audiosPurged, int tenantsVisited, int tenantsSkipped, int tenantsFailed) {
    }

    public Summary purgeAllTenants() {
        return purgeAllTenants(Instant.now());
    }

    public Summary purgeAllTenants(Instant now) {
        List<AudioRetentionPolicy> policies = workspace.listAudioRetentionPolicies();
        int purged = 0;
        int visited = 0;
        int skipped = 0;
        int failed = 0;
        for (AudioRetentionPolicy policy : policies) {
            if (policy.keepsAudioForever()) {
                skipped++;
                continue;
            }
            try {
                purged += purgeTenant(policy, now);
                visited++;
            } catch (RuntimeException e) {
                failed++;
                log.error("Audio retention purge failed for tenant {}; continuing with the next one", policy.tenantId(),
                        e);
            }
        }
        log.info(
                "Audio retention purge finished: {} audio(s) deleted, {} tenant(s) visited, {} keeping audio forever, {} failed",
                purged, visited, skipped, failed);
        return new Summary(purged, visited, skipped, failed);
    }

    private int purgeTenant(AudioRetentionPolicy policy, Instant now) {
        Instant storedBefore = now.minus(Duration.ofDays(policy.retentionDays()));
        int batchSize = properties.batchSize();
        int[] total = {0};
        TenantContext.runWith(new TenantContext.TenantSnapshot(policy.tenantId(), policy.tenantSchema()), () -> {
            int batch;
            do {
                batch = purgeBatch.handle(new PurgeExpiredAudiosCommand(storedBefore, now, batchSize));
                total[0] += batch;
            } while (batch >= batchSize);
        });
        if (total[0] > 0) {
            log.debug("Tenant {}: {} expired audio(s) deleted (retention {} day(s))", policy.tenantId(), total[0],
                    policy.retentionDays());
        }
        return total[0];
    }
}
