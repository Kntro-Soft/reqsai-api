package com.kntro.reqsai.discovery.application.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the scheduled purge of expired audios (US69), bound from
 * {@code reqsai.discovery.audio-retention.*}. The retention period itself is a
 * per-organization setting (US15); this only controls the job.
 *
 * @param enabled
 *            whether the scheduled purge runs at all (off unless switched on,
 *            so tests and local runs never delete anything by surprise)
 * @param cron
 *            Spring cron expression (UTC) of the purge
 * @param batchSize
 *            audios deleted per transaction; a tenant with more is drained in
 *            several batches
 */
@Validated
@ConfigurationProperties(prefix = "reqsai.discovery.audio-retention")
public record AudioRetentionProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("0 30 3 * * *") @NotBlank String cron, @DefaultValue("200") @Positive int batchSize) {
}
