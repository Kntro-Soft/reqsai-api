package com.kntro.reqsai.workspace.api;

/**
 * The audio retention policy of one organization (US15) together with the
 * coordinates needed to reach its tenant schema, so a scheduled job of another
 * module can visit every tenant without reading the organization registry
 * itself.
 *
 * @param tenantId
 *            the organization (tenant) id, as bound to {@code TenantContext}
 * @param tenantSchema
 *            the PostgreSQL schema holding the organization's data
 * @param retentionDays
 *            days an audio is kept after transcription: {@code 0} = delete
 *            immediately, {@code -1} = keep forever
 */
public record AudioRetentionPolicy(String tenantId, String tenantSchema, int retentionDays) {

    /** Whether the organization asked to keep its audios forever. */
    public boolean keepsAudioForever() {
        return retentionDays < 0;
    }
}
