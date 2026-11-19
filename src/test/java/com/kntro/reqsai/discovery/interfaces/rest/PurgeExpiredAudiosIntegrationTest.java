package com.kntro.reqsai.discovery.interfaces.rest;

import com.kntro.reqsai.discovery.application.service.PurgeExpiredAudiosService;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.StubRequirementGenerationConfig;
import com.kntro.reqsai.testsupport.StubTranscriptionConfig;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the purge of expired audios (US69) against real tenant
 * schemas: organizations are created through the API (which provisions
 * {@code tenant_<slug>}), recordings are uploaded through the API, their age is
 * moved back in the tenant schema, and the scheduled process is run through
 * {@link PurgeExpiredAudiosService} (the scheduler itself is disabled in the
 * test profile). The test profile also sets a batch size of 2, so a tenant with
 * three expired audios is drained in two batches.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@Import({StubTranscriptionConfig.class, StubRequirementGenerationConfig.class})
@DisplayName("Integration: Purge expired audios (US69)")
class PurgeExpiredAudiosIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PurgeExpiredAudiosService purge;

    @Test
    @DisplayName("Given an organization with a 30-day retention, when the scheduled process finds audios older than 30 days, then it deletes them and keeps the transcript and the stories")
    void should_delete_expired_audios_and_keep_transcript_and_stories() {
        Tenant tenant = provisionTenant(30);
        UUID withStories = uploadSession(tenant);
        UUID expiredTwo = uploadSession(tenant);
        UUID expiredThree = uploadSession(tenant);
        UUID recent = uploadSession(tenant);
        process(tenant, withStories);
        for (UUID id : new UUID[]{withStories, expiredTwo, expiredThree}) {
            ageAudio(tenant, id, 31);
        }
        ageAudio(tenant, recent, 5);
        assertThat(audioBytesStored(tenant, withStories)).isTrue();
        long storiesBefore = storyCount(tenant, withStories);
        assertThat(storiesBefore).isPositive();

        PurgeExpiredAudiosService.Summary summary = purge.purgeAllTenants();

        assertThat(summary.audiosPurged()).isGreaterThanOrEqualTo(3);
        for (UUID id : new UUID[]{withStories, expiredTwo, expiredThree}) {
            assertThat(audioBytesStored(tenant, id)).as("audio bytes of %s", id).isFalse();
            assertThat(audioPurgedAt(tenant, id)).as("purge mark of %s", id).isNotNull();
            assertThat(transcript(tenant, id)).as("transcript of %s", id)
                    .isEqualTo(StubTranscriptionConfig.STUB_TRANSCRIPT);
        }
        assertThat(storyCount(tenant, withStories)).isEqualTo(storiesBefore);
        assertThat(audioBytesStored(tenant, recent)).isTrue();
        assertThat(audioPurgedAt(tenant, recent)).isNull();

        String body = getSession(tenant, withStories);
        assertThat(body).contains("\"audioState\":\"PURGED\"").doesNotContain("\"audioPurgedAt\":null");
    }

    @Test
    @DisplayName("Given an audio linked to a session still in progress, when the scheduled process runs, then it does not delete that audio")
    void should_not_delete_the_audio_of_a_session_in_progress() {
        Tenant tenant = provisionTenant(30);
        UUID recording = uploadSession(tenant);
        UUID processing = uploadSession(tenant);
        UUID finished = uploadSession(tenant);
        for (UUID id : new UUID[]{recording, processing, finished}) {
            ageAudio(tenant, id, 90);
        }
        setStatus(tenant, recording, "RECORDING");
        setStatus(tenant, processing, "PROCESSING");

        purge.purgeAllTenants();

        assertThat(audioBytesStored(tenant, recording)).isTrue();
        assertThat(audioPurgedAt(tenant, recording)).isNull();
        assertThat(audioBytesStored(tenant, processing)).isTrue();
        assertThat(audioPurgedAt(tenant, processing)).isNull();
        assertThat(audioBytesStored(tenant, finished)).isFalse();
        assertThat(audioPurgedAt(tenant, finished)).isNotNull();

        setStatus(tenant, recording, "STOPPED");
        setStatus(tenant, processing, "COMPLETED");
        purge.purgeAllTenants();

        assertThat(audioBytesStored(tenant, recording)).isFalse();
        assertThat(audioPurgedAt(tenant, recording)).isNotNull();
        assertThat(audioBytesStored(tenant, processing)).isFalse();
    }

    @Test
    @DisplayName("should apply each organization's own retention period and never touch one that keeps audios forever")
    void should_apply_each_tenants_own_policy() {
        Tenant thirtyDays = provisionTenant(30);
        Tenant ninetyDays = provisionTenant(90);
        Tenant forever = provisionTenant(-1);
        UUID a = uploadSession(thirtyDays);
        UUID b = uploadSession(ninetyDays);
        UUID c = uploadSession(forever);
        ageAudio(thirtyDays, a, 40);
        ageAudio(ninetyDays, b, 40);
        ageAudio(forever, c, 4000);

        purge.purgeAllTenants();

        assertThat(audioPurgedAt(thirtyDays, a)).isNotNull();
        assertThat(audioPurgedAt(ninetyDays, b)).isNull();
        assertThat(audioBytesStored(ninetyDays, b)).isTrue();
        assertThat(audioPurgedAt(forever, c)).isNull();
        assertThat(audioBytesStored(forever, c)).isTrue();
    }

    @Test
    @DisplayName("should be idempotent: a second run deletes nothing more and leaves the purge mark as it was")
    void should_be_idempotent() {
        Tenant tenant = provisionTenant(30);
        UUID session = uploadSession(tenant);
        ageAudio(tenant, session, 31);

        purge.purgeAllTenants();
        Object firstMark = audioPurgedAt(tenant, session);
        PurgeExpiredAudiosService.Summary second = purge.purgeAllTenants();

        assertThat(firstMark).isNotNull();
        assertThat(second.audiosPurged()).isZero();
        assertThat(second.tenantsFailed()).isZero();
        assertThat(audioPurgedAt(tenant, session)).isEqualTo(firstMark);
    }

    @Test
    @DisplayName("should not store the recording when the organization deletes audios immediately (0 days)")
    void should_not_store_the_recording_when_retention_is_zero() {
        Tenant tenant = provisionTenant(0);

        UUID session = uploadSession(tenant);

        assertThat(audioBytesStored(tenant, session)).isFalse();
        assertThat(audioStoredAt(tenant, session)).isNull();
        assertThat(getSession(tenant, session)).contains("\"audioState\":\"NONE\"");
    }

    @Test
    @DisplayName("should report an uploaded recording as STORED until the retention period runs out")
    void should_report_the_recording_as_stored() {
        Tenant tenant = provisionTenant(30);

        UUID session = uploadSession(tenant);

        assertThat(getSession(tenant, session)).contains("\"audioState\":\"STORED\"")
                .contains("\"audioPurgedAt\":null");
        assertThat(audioStoredAt(tenant, session)).isNotNull();
    }

    // -- helpers
    // --------------------------------------------------------------------------------

    private record Tenant(String orgId, String schema, String token) {
    }

    private Tenant provisionTenant(int retentionDays) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orgId = jdbc.queryForObject("SELECT id::text FROM public.organizations WHERE slug = ?", String.class,
                "acme-" + suffix);
        jdbc.update("UPDATE public.organizations SET audio_retention_days = ? WHERE id = ?::uuid", retentionDays,
                orgId);
        return new Tenant(orgId, "tenant_acme-" + suffix, TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"));
    }

    private UUID uploadSession(Tenant tenant) {
        ResponseEntity<String> created = client().post().uri("/api/projects/{p}/sessions", UUID.randomUUID())
                .header("Authorization", tenant.token()).header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Reunión de requisitos", "language", "es-PE"))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID sessionId = UUID.fromString(created.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("fake-audio-bytes".getBytes()) {
            @Override
            public String getFilename() {
                return "reunion.mp3";
            }
        });
        ResponseEntity<String> uploaded = client().post().uri("/api/sessions/{id}/upload", sessionId)
                .header("Authorization", tenant.token()).header("Api-Version", "1")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        return sessionId;
    }

    private void process(Tenant tenant, UUID sessionId) {
        ResponseEntity<String> res = client().post().uri("/api/sessions/{id}/process", sessionId)
                .header("Authorization", tenant.token()).header("Api-Version", "1")
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String getSession(Tenant tenant, UUID sessionId) {
        UUID projectId = jdbc.queryForObject(
                "SELECT project_id FROM \"%s\".discovery_sessions WHERE id = ?".formatted(tenant.schema()), UUID.class,
                sessionId);
        ResponseEntity<String> res = client().get().uri("/api/projects/{p}/sessions/{s}", projectId, sessionId)
                .header("Authorization", tenant.token()).header("Api-Version", "1")
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private void ageAudio(Tenant tenant, UUID sessionId, int days) {
        jdbc.update(
                "UPDATE \"%s\".discovery_sessions SET audio_stored_at = now() - make_interval(days => ?) WHERE id = ?"
                        .formatted(tenant.schema()),
                days, sessionId);
    }

    private void setStatus(Tenant tenant, UUID sessionId, String status) {
        jdbc.update("UPDATE \"%s\".discovery_sessions SET status = ? WHERE id = ?".formatted(tenant.schema()), status,
                sessionId);
    }

    private boolean audioBytesStored(Tenant tenant, UUID sessionId) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".session_audio_contents WHERE session_id = ?".formatted(tenant.schema()),
                Integer.class, sessionId);
        return rows != null && rows > 0;
    }

    private Object audioPurgedAt(Tenant tenant, UUID sessionId) {
        return jdbc.queryForObject(
                "SELECT audio_purged_at FROM \"%s\".discovery_sessions WHERE id = ?".formatted(tenant.schema()),
                Object.class, sessionId);
    }

    private Object audioStoredAt(Tenant tenant, UUID sessionId) {
        return jdbc.queryForObject(
                "SELECT audio_stored_at FROM \"%s\".discovery_sessions WHERE id = ?".formatted(tenant.schema()),
                Object.class, sessionId);
    }

    private String transcript(Tenant tenant, UUID sessionId) {
        return jdbc.queryForObject(
                "SELECT transcript FROM \"%s\".discovery_sessions WHERE id = ?".formatted(tenant.schema()),
                String.class, sessionId);
    }

    private long storyCount(Tenant tenant, UUID sessionId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".user_stories WHERE session_id = ?".formatted(tenant.schema()), Long.class,
                sessionId);
        return count == null ? 0 : count;
    }
}
