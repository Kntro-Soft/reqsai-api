package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.command.PurgeExpiredAudiosCommand;
import com.kntro.reqsai.discovery.application.config.AudioRetentionProperties;
import com.kntro.reqsai.discovery.application.handler.PurgeExpiredAudiosCommandHandler;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.api.AudioRetentionPolicy;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PurgeExpiredAudiosService} (US69): it visits each
 * tenant under its own {@link TenantContext}, derives the cutoff from the
 * organization's retention period, drains big tenants in batches, skips
 * organizations that keep audio forever and isolates failures.
 */
@DisplayName("Application: Purge Expired Audios service (tenant iteration)")
@ExtendWith(MockitoExtension.class)
class PurgeExpiredAudiosServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T03:30:00Z");
    private static final int BATCH = 2;

    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private PurgeExpiredAudiosCommandHandler handler;

    private PurgeExpiredAudiosService service;

    @BeforeEach
    void setUp() {
        service = new PurgeExpiredAudiosService(workspace, handler,
                new AudioRetentionProperties(true, "0 30 3 * * *", BATCH));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Given a 30-day organization, when the process runs, then it purges its tenant schema with the 30-day cutoff and clears the context")
    void should_purge_each_tenant_under_its_own_context() {
        when(workspace.listAudioRetentionPolicies()).thenReturn(List.of(
                new AudioRetentionPolicy("org-a", "tenant_a", 30), new AudioRetentionPolicy("org-b", "tenant_b", 7)));
        List<String> schemasSeen = new ArrayList<>();
        when(handler.handle(any())).thenAnswer(invocation -> {
            schemasSeen.add(TenantContext.getCurrentTenant() + "@" + TenantContext.getCurrentSchema());
            return 1;
        });

        PurgeExpiredAudiosService.Summary summary = service.purgeAllTenants(NOW);

        assertThat(schemasSeen).containsExactly("org-a@tenant_a", "org-b@tenant_b");
        ArgumentCaptor<PurgeExpiredAudiosCommand> commands = ArgumentCaptor.forClass(PurgeExpiredAudiosCommand.class);
        verify(handler, times(2)).handle(commands.capture());
        assertThat(commands.getAllValues().get(0).storedBefore()).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(commands.getAllValues().get(1).storedBefore()).isEqualTo(NOW.minus(Duration.ofDays(7)));
        assertThat(commands.getAllValues()).allSatisfy(c -> {
            assertThat(c.now()).isEqualTo(NOW);
            assertThat(c.batchSize()).isEqualTo(BATCH);
        });
        assertThat(summary).isEqualTo(new PurgeExpiredAudiosService.Summary(2, 2, 0, 0));
        assertThat(TenantContext.getCurrentTenant()).isNull();
        assertThat(TenantContext.getCurrentSchema()).isNull();
    }

    @Test
    @DisplayName("should use the current instant as the cutoff base when no instant is given")
    void should_default_to_the_current_instant() {
        when(workspace.listAudioRetentionPolicies())
                .thenReturn(List.of(new AudioRetentionPolicy("org-a", "tenant_a", 0)));
        when(handler.handle(any())).thenReturn(0);
        Instant before = Instant.now();

        service.purgeAllTenants();

        ArgumentCaptor<PurgeExpiredAudiosCommand> command = ArgumentCaptor.forClass(PurgeExpiredAudiosCommand.class);
        verify(handler).handle(command.capture());
        assertThat(command.getValue().now()).isBetween(before, Instant.now());
        assertThat(command.getValue().storedBefore()).isEqualTo(command.getValue().now());
    }

    @Test
    @DisplayName("should not touch an organization that keeps its audios forever")
    void should_skip_organizations_that_keep_audio_forever() {
        when(workspace.listAudioRetentionPolicies())
                .thenReturn(List.of(new AudioRetentionPolicy("org-a", "tenant_a", -1)));

        PurgeExpiredAudiosService.Summary summary = service.purgeAllTenants(NOW);

        verify(handler, never()).handle(any());
        assertThat(summary).isEqualTo(new PurgeExpiredAudiosService.Summary(0, 0, 1, 0));
    }

    @Test
    @DisplayName("should keep purging a tenant in batches until a batch comes back short")
    void should_drain_a_tenant_in_batches() {
        when(workspace.listAudioRetentionPolicies())
                .thenReturn(List.of(new AudioRetentionPolicy("org-a", "tenant_a", 30)));
        when(handler.handle(any())).thenReturn(BATCH, BATCH, 1);

        PurgeExpiredAudiosService.Summary summary = service.purgeAllTenants(NOW);

        verify(handler, times(3)).handle(any());
        assertThat(summary.audiosPurged()).isEqualTo(2 * BATCH + 1);
    }

    @Test
    @DisplayName("should keep going with the next tenant when one fails, and clear the context")
    void should_isolate_a_failing_tenant() {
        when(workspace.listAudioRetentionPolicies()).thenReturn(List.of(
                new AudioRetentionPolicy("org-a", "tenant_a", 30), new AudioRetentionPolicy("org-b", "tenant_b", 30)));
        when(handler.handle(any())).thenAnswer(invocation -> {
            if ("org-a".equals(TenantContext.getCurrentTenant())) {
                throw new IllegalStateException("schema is gone");
            }
            return 1;
        });

        PurgeExpiredAudiosService.Summary summary = service.purgeAllTenants(NOW);

        assertThat(summary).isEqualTo(new PurgeExpiredAudiosService.Summary(1, 1, 0, 1));
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("should do nothing when there are no organizations")
    void should_do_nothing_without_organizations() {
        when(workspace.listAudioRetentionPolicies()).thenReturn(List.of());

        assertThat(service.purgeAllTenants(NOW)).isEqualTo(new PurgeExpiredAudiosService.Summary(0, 0, 0, 0));
        verify(handler, never()).handle(any());
    }
}
