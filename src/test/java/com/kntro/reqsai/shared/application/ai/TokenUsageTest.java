package com.kntro.reqsai.shared.application.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Shared: TokenUsage quota weight")
class TokenUsageTest {

    @Test
    @DisplayName("uncached input and output count in full")
    void uncached_counts_in_full() {
        TokenUsage usage = TokenUsage.of(AiFeature.CHAT, null, "m", 1_000, 200, 0, "k");

        assertThat(usage.totalTokens()).isEqualTo(1_200);
        assertThat(usage.quotaTokens()).isEqualTo(1_200);
    }

    @Test
    @DisplayName("cached input counts at half weight, rounded up")
    void cached_input_counts_half() {
        TokenUsage usage = TokenUsage.of(AiFeature.LIVE_SUGGESTIONS, null, "m", 6_000, 400, 4_001, "k");

        assertThat(usage.quotaTokens()).isEqualTo(1_999 + 2_001 + 400);
        assertThat(usage.totalTokens()).isEqualTo(6_400);
    }

    @Test
    @DisplayName("cached tokens can never exceed the input, and negatives are clamped")
    void clamps_inconsistent_figures() {
        TokenUsage usage = TokenUsage.of(AiFeature.CHAT, null, null, 100, -5, 500, "k");

        assertThat(usage.cachedInputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isZero();
        assertThat(usage.quotaTokens()).isEqualTo(50);
    }

    @Test
    @DisplayName("embeddings count at a tenth, rounded up")
    void embeddings_weighted_down() {
        assertThat(TokenUsage.of(AiFeature.EMBEDDINGS, null, "e", 1_000, 0, 0, "k").quotaTokens()).isEqualTo(100);
        assertThat(TokenUsage.of(AiFeature.EMBEDDINGS, null, "e", 3, 0, 0, "k").quotaTokens()).isEqualTo(1);
    }

    @Test
    @DisplayName("speech-to-text is metered in seconds and never counts against the token quota")
    void audio_has_no_quota_tokens() {
        TokenUsage usage = TokenUsage.audio(UUID.randomUUID(), "stt", 25);

        assertThat(usage.seconds()).isEqualTo(25);
        assertThat(usage.quotaTokens()).isZero();
    }

    @Test
    @DisplayName("a missing idempotency key is replaced by a unique one")
    void generates_key() {
        TokenUsage a = TokenUsage.of(AiFeature.CHAT, null, null, 1, 1, 0, null);
        TokenUsage b = TokenUsage.of(AiFeature.CHAT, null, null, 1, 1, 0, " ");

        assertThat(a.idempotencyKey()).isNotBlank().isNotEqualTo(b.idempotencyKey());
    }

    @Test
    @DisplayName("an open feature and project are filled from the surrounding scope")
    void scope_fills_open_fields() {
        UUID project = UUID.randomUUID();
        TokenUsage open = TokenUsage.of(AiFeature.OTHER, null, "m", 10, 0, 0, "k");

        TokenUsage resolved = AiUsageScope.call(AiFeature.CHAT, project, () -> open.within(AiUsageScope.current()));

        assertThat(resolved.feature()).isEqualTo(AiFeature.CHAT);
        assertThat(resolved.projectId()).isEqualTo(project);
        assertThat(AiUsageScope.current()).isNull();
    }

    @Test
    @DisplayName("a feature the caller already chose is kept inside a scope")
    void scope_keeps_explicit_feature() {
        TokenUsage embedding = TokenUsage.of(AiFeature.EMBEDDINGS, null, "m", 10, 0, 0, "k");

        TokenUsage resolved = AiUsageScope.call(AiFeature.CHAT, null, () -> embedding.within(AiUsageScope.current()));

        assertThat(resolved.feature()).isEqualTo(AiFeature.EMBEDDINGS);
    }
}
