package com.kntro.reqsai.discovery.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline evidence for the realtime duplicate filter, on REAL embedding vectors instead of hand-made ones.
 *
 * <p>The fixture holds the 768-dimension vectors {@code nomic-embed-text} (Ollama) returned for the
 * candidate text ({@code "<title>. As <role>, I want to <action>, so that <benefit>."}) of plausible drafts
 * from the production meeting that lost a rule: booking a medical appointment, the 10% penalty for
 * cancelling with less than 24 hours, the doctor's e-mail notification, a plain cancellation, and three
 * paraphrases of the booking. Production embeds with OpenAI {@code text-embedding-3-small}, whose scores
 * differ in absolute value; the point here is the shape, which no single cosine bar can fix:
 * <ul>
 *   <li>distinct requirements of the same domain score ABOVE the 0.84 bar (the doctor's notification
 *       0.88, a cancellation 0.85), so the old cosine-only filter dropped them;</li>
 *   <li>a synonym paraphrase of the booking scores BELOW it (0.83).</li>
 * </ul>
 * Regenerate the fixture with any 768-dimension model; the assertions on the policy do not depend on it.
 */
@DisplayName("Application: realtime duplicate filter on real embedding vectors (nomic-embed-text)")
class SuggestionDedupRealVectorsTest {

    private static final double OLD_BAR = 0.84;
    private static final String FIXTURE = "/discovery/dedup/nomic-embed-text-medical-booking.json";

    private static Map<String, FixtureDraft> drafts;

    private final SuggestionDedupPolicy policy = new SuggestionDedupPolicy(OLD_BAR);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FixtureDraft(String label, String title, String role, String action, String benefit, String vector) {
        float[] embedding() {
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(vector)).order(ByteOrder.LITTLE_ENDIAN);
            float[] out = new float[bytes.remaining() / Float.BYTES];
            bytes.asFloatBuffer().get(out);
            return out;
        }

        String candidateText() {
            return "%s. As %s, I want to %s, so that %s.".formatted(title, role, action, benefit);
        }

        SuggestionDedupPolicy.Draft draft() {
            return new SuggestionDedupPolicy.Draft(SuggestionType.NEW_STORY, title, role, action, benefit, List.of());
        }

        GenerationResult.GeneratedStory generated() {
            return new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY, title, role, action, benefit,
                    Priority.HIGH, 3, List.of(), null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fixture(String model, List<FixtureDraft> drafts) {}

    @BeforeAll
    static void loadFixture() throws IOException {
        try (InputStream in = SuggestionDedupRealVectorsTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in).as("fixture %s on the test classpath", FIXTURE).isNotNull();
            Fixture fixture = new ObjectMapper().readValue(in, Fixture.class);
            drafts = new LinkedHashMap<>();
            fixture.drafts().forEach(d -> drafts.put(d.label(), d));
        }
        assertThat(drafts.values()).allSatisfy(d -> assertThat(d.embedding()).hasSize(EmbeddingPort.DIMENSIONS));
    }

    private static double cosine(String a, String b) {
        return SuggestionCreationService.cosineSimilarity(drafts.get(a).embedding(), drafts.get(b).embedding());
    }

    @Test
    @DisplayName("measured: no single cosine bar separates distinct same-domain requirements from paraphrases")
    void no_single_bar_separates_them() {
        // Distinct requirements the old 0.84 bar treated as duplicates of the booking.
        assertThat(cosine("notificationPortal", "booking")).isGreaterThan(OLD_BAR);
        assertThat(cosine("cancellation", "booking")).isGreaterThan(OLD_BAR);
        // ...and the penalty as a duplicate of the cancellation.
        assertThat(cosine("penalty", "cancellation")).isGreaterThan(OLD_BAR);
        // A synonym paraphrase of the booking that the bar let through.
        assertThat(cosine("synonymParaphrase", "booking")).isLessThan(OLD_BAR);
        // A cancellation that also states the penalty in words scores like a restatement of the cancellation.
        assertThat(cosine("penaltySpelledOut", "cancellation")).isGreaterThan(0.95);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"penalty", "penaltyCancel", "penaltySpelledOut", "notification", "notificationPortal",
            "cancellation"})
    @DisplayName("every distinct requirement is kept next to the booking, whatever its cosine")
    void distinct_requirements_are_not_repeats_of_booking(String label) {
        var verdict = policy.repeats(drafts.get(label).draft(), drafts.get("booking").draft(),
                cosine(label, "booking"), false);

        assertThat(verdict.duplicate()).as("%s vs booking: %s", label, verdict.reason()).isFalse();
    }

    @Test
    @DisplayName("the spelled-out penalty is kept next to a plain cancellation, although their cosine is 0.96")
    void spelled_out_penalty_is_not_a_repeat_of_cancellation() {
        var verdict = policy.repeats(drafts.get("penaltySpelledOut").draft(), drafts.get("cancellation").draft(),
                cosine("penaltySpelledOut", "cancellation"), false);

        assertThat(verdict.duplicate()).as(verdict.reason()).isFalse();
    }

    @Test
    @DisplayName("a close paraphrase of the booking is still dropped as a repeat")
    void close_paraphrase_is_a_repeat() {
        var verdict = policy.repeats(drafts.get("closeParaphrase").draft(), drafts.get("booking").draft(),
                cosine("closeParaphrase", "booking"), false);

        assertThat(verdict.duplicate()).as(verdict.reason()).isTrue();
    }

    @Test
    @DisplayName("one realtime pass with the meeting's requirements keeps every rule and drops only the paraphrase")
    void one_pass_keeps_every_rule() {
        List<String> emitted = List.of("booking", "penalty", "notificationPortal", "cancellation", "closeParaphrase");

        // What the old cosine-only filter did: drop a draft at >= 0.84 to any draft kept before it.
        List<String> keptByOldBar = new ArrayList<>();
        for (String label : emitted) {
            if (keptByOldBar.stream().noneMatch(k -> cosine(label, k) >= OLD_BAR)) {
                keptByOldBar.add(label);
            }
        }
        assertThat(keptByOldBar).as("the old bar swallowed the notification and the cancellation")
                .containsExactly("booking", "penalty");

        List<Suggestion> created = serviceWithFixtureEmbeddings().createSuggestions(
                new GenerationResult(emitted.stream().map(l -> drafts.get(l).generated()).toList(), List.of()),
                UUID.randomUUID(), UUID.randomUUID());

        assertThat(created).extracting(Suggestion::getDraftTitle).containsExactly(
                drafts.get("booking").title(), drafts.get("penalty").title(),
                drafts.get("notificationPortal").title(), drafts.get("cancellation").title());
        assertThat(created).allSatisfy(s -> assertThat(s.getType()).isEqualTo(SuggestionType.NEW_STORY));
    }

    /** The real service, embedding each candidate text with its fixture vector; empty backlog and session. */
    private SuggestionCreationService serviceWithFixtureEmbeddings() {
        Map<String, float[]> byText = new LinkedHashMap<>();
        drafts.values().forEach(d -> byText.put(d.candidateText(), d.embedding()));
        EmbeddingPort embeddings = new EmbeddingPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public float[] embed(String text) {
                float[] vector = byText.get(text);
                assertThat(vector).as("fixture vector for '%s'", text).isNotNull();
                return vector;
            }
        };
        SuggestionRepository suggestions = mock(SuggestionRepository.class);
        when(suggestions.findAllBySessionIdAndStatus(any(), any())).thenReturn(List.of());
        when(suggestions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UserStoryRepository stories = mock(UserStoryRepository.class);
        when(stories.findMostSimilar(any(), any())).thenReturn(Optional.empty());
        return new SuggestionCreationService(suggestions, stories, embeddings, policy, new SuggestionTargetPolicy(0.60, 0.05));
    }
}
