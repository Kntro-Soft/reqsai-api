package com.kntro.reqsai.overview.domain.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Domain: text similarity between the proposed and the final story")
class TextSimilarityTest {

    @ParameterizedTest(name = "distance({0}, {1}) = {2}")
    @CsvSource({"kitten,sitting,3", "flaw,lawn,2", "abc,abc,0", "abc,'',3", "'',abc,3", "'','',0", "abcdef,abcxef,1",
            "prefix-same-a,prefix-same-b,1", "a-suffix,b-suffix,1"})
    @DisplayName("Levenshtein distance counts single-character edits")
    void distance(String a, String b, int expected) {
        assertThat(TextSimilarity.distance(a, b)).isEqualTo(expected);
        assertThat(TextSimilarity.distance(b, a)).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalization ignores case, accents, punctuation and spacing")
    void normalize() {
        assertThat(TextSimilarity.normalize("  Reservar  MESA,\nel Cliente: ¡Sí!  ")).isEqualTo("reservar mesa el cliente si");
        assertThat(TextSimilarity.normalize("")).isEmpty();
        assertThat(TextSimilarity.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("identical text, or text that only differs in case, accents and punctuation, is fully similar")
    void identical() {
        assertThat(TextSimilarity.similarity("Reservar una mesa", "Reservar una mesa")).isEqualTo(1.0);
        assertThat(TextSimilarity.similarity("Reservar una mesa.", "reservár UNA mesa")).isEqualTo(1.0);
        assertThat(TextSimilarity.similarity("", "  ")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("completely different text has no similarity")
    void different() {
        assertThat(TextSimilarity.similarity("aaaa", "bbbb")).isZero();
        assertThat(TextSimilarity.similarity("abc", "")).isZero();
    }

    @Test
    @DisplayName("similarity is 1 minus the edits over the longer text")
    void ratio() {
        // 2 edits over 10 characters.
        assertThat(TextSimilarity.similarity("abcdefghij", "abcdefghxx")).isCloseTo(0.8, within(1e-9));
        // 1 insertion over 5 characters.
        assertThat(TextSimilarity.similarity("abcd", "abcde")).isCloseTo(0.8, within(1e-9));
    }

    @Test
    @DisplayName("long texts are compared without blowing up")
    void longTexts() {
        String a = "palabra ".repeat(600);
        String b = a.replace("palabra palabra palabra", "otra cosa distinta");
        assertThat(TextSimilarity.similarity(a, b)).isBetween(0.0, 1.0);
    }
}
