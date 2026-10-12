package com.kntro.reqsai.billing.infrastructure.persistence.adapters;

import com.kntro.reqsai.billing.application.port.AiUsageLedgerPort;
import com.kntro.reqsai.shared.application.ai.TokenUsage;
import com.kntro.reqsai.shared.domain.support.IdGenerator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link AiUsageLedgerPort} over plain SQL on the tenant-schema table {@code ai_usage_events}. Native
 * queries keep the append-only table free of a mapped entity: nothing ever loads or updates a row.
 */
@Repository
@RequiredArgsConstructor
public class AiUsageLedgerAdapter implements AiUsageLedgerPort {

    private final EntityManager entityManager;

    @Override
    public boolean append(TokenUsage usage, Instant at) {
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO ai_usage_events (id, project_id, feature, model, input_tokens, output_tokens,
                                                     cached_input_tokens, quota_tokens, audio_seconds,
                                                     idempotency_key, created_at)
                        VALUES (:id, :projectId, :feature, :model, :input, :output, :cached, :quota, :seconds,
                                :key, :at)
                        ON CONFLICT (idempotency_key) DO NOTHING""")
                .setParameter("id", IdGenerator.newId())
                .setParameter("projectId", usage.projectId())
                .setParameter("feature", usage.feature().name())
                .setParameter("model", truncate(usage.model(), 100))
                .setParameter("input", usage.inputTokens())
                .setParameter("output", usage.outputTokens())
                .setParameter("cached", usage.cachedInputTokens())
                .setParameter("quota", usage.quotaTokens())
                .setParameter("seconds", usage.seconds())
                .setParameter("key", truncate(usage.idempotencyKey(), 200))
                .setParameter("at", at)
                .executeUpdate();
        return inserted > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Summary summarize(Instant from, Instant to, int topProjects) {
        List<Object[]> features = entityManager.createNativeQuery("""
                        SELECT feature, SUM(quota_tokens), SUM(input_tokens), SUM(output_tokens),
                               SUM(cached_input_tokens), COUNT(*)
                        FROM ai_usage_events
                        WHERE created_at >= :from AND created_at < :to AND feature <> 'TRANSCRIPTION'
                        GROUP BY feature
                        ORDER BY SUM(quota_tokens) DESC""")
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        List<Object[]> projects = entityManager.createNativeQuery("""
                        SELECT project_id, SUM(quota_tokens)
                        FROM ai_usage_events
                        WHERE created_at >= :from AND created_at < :to AND project_id IS NOT NULL
                          AND quota_tokens > 0
                        GROUP BY project_id
                        ORDER BY SUM(quota_tokens) DESC
                        LIMIT :limit""")
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("limit", topProjects)
                .getResultList();
        Number seconds = (Number) entityManager.createNativeQuery("""
                        SELECT COALESCE(SUM(audio_seconds), 0)
                        FROM ai_usage_events
                        WHERE created_at >= :from AND created_at < :to AND feature = 'TRANSCRIPTION'""")
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return new Summary(
                features.stream().map(r -> new FeatureTotals((String) r[0], n(r[1]), n(r[2]), n(r[3]), n(r[4]),
                        n(r[5]))).toList(),
                projects.stream().map(r -> new ProjectTotals((UUID) r[0], n(r[1]))).toList(),
                seconds.longValue());
    }

    private static long n(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
