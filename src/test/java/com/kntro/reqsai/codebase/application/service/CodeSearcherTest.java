package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Codebase: lexical code search")
class CodeSearcherTest {

    private static final String POLICY = """
            /** Reservations can be cancelled only up to 2 hours before the booked time. */
            export const CANCELLATION_LIMIT_HOURS = 2;
            export function canCancel(startsAt: Date, now: Date): boolean {
              return (startsAt.getTime() - now.getTime()) / 3_600_000 >= CANCELLATION_LIMIT_HOURS;
            }
            """;

    private static final List<SourceFile> FILES = List.of(
            new SourceFile("src/reservations/cancellation.policy.ts", POLICY),
            new SourceFile("src/payments/payment.service.ts", """
                    export function payWithYape(amount: number) { return amount; }
                    export function payWithCard(amount: number) { return amount; }
                    """),
            new SourceFile("src/menu/dishes.ts", "export const DISHES = ['ceviche', 'lomo saltado'];"),
            new SourceFile("README.md", "# Reservas\nSistema de reservas en línea del restaurante."));

    @Test
    @DisplayName("a Spanish request finds English code through the shared word root")
    void spanishRequestFindsEnglishCode() {
        var query = CodeSearcher.parse("El comensal quiere cancelar su reserva hasta 24 horas antes");

        var passages = CodeSearcher.search(FILES, query, Set.of(), 5, 4000, () -> false);

        assertThat(passages).isNotEmpty();
        assertThat(passages.getFirst().path()).isEqualTo("src/reservations/cancellation.policy.ts");
        assertThat(passages.getFirst().text()).contains("CANCELLATION_LIMIT_HOURS = 2");
        assertThat(passages).noneMatch(p -> p.path().contains("dishes"));
    }

    @Test
    @DisplayName("identifiers in the request weigh double and match their parts")
    void identifiersMatchTheirParts() {
        var query = CodeSearcher.parse("revisar `payWithYape`");

        assertThat(query.stems()).containsKeys("pay", "yape");
        assertThat(query.stems().get("yape")).isEqualTo(2.0);

        var passages = CodeSearcher.search(FILES, query, Set.of(), 5, 4000, () -> false);
        assertThat(passages.getFirst().path()).isEqualTo("src/payments/payment.service.ts");
    }

    @Test
    @DisplayName("stop words and short words are not terms; numbers are")
    void parseDropsNoise() {
        var query = CodeSearcher.parse("para que el cliente pueda pagar con 24 horas de plazo");

        assertThat(query.stems()).doesNotContainKeys("para", "pueda", "con", "de", "que");
        assertThat(query.stems()).containsKeys("client", "pagar", "24", "horas", "plazo");
    }

    @Test
    @DisplayName("line numbers are 1-based and tightened around the hits")
    void lineRangesPointAtTheHits() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 100; i++) lines.add(i == 60 ? "const PENALTY_PERCENT = 10;" : "// filler " + i);
        var files = List.of(new SourceFile("src/billing/penalty.ts", String.join("\n", lines)));

        var passages = CodeSearcher.search(files, CodeSearcher.parse("penalty percent"), Set.of(), 3, 4000,
                () -> false);

        assertThat(passages).hasSize(1);
        assertThat(passages.getFirst().lineStart()).isLessThanOrEqualTo(60).isGreaterThanOrEqualTo(55);
        assertThat(passages.getFirst().lineEnd()).isGreaterThanOrEqualTo(60).isLessThanOrEqualTo(65);
        String[] shown = passages.getFirst().text().split("\n");
        assertThat(shown[60 - passages.getFirst().lineStart()]).isEqualTo("const PENALTY_PERCENT = 10;");
    }

    @Test
    @DisplayName("the total size, the passage count and the line length are bounded")
    void resultIsBounded() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 400; i++) big.append("cancel reservation handler ").append(i).append(' ').append("x".repeat(300)).append('\n');
        List<SourceFile> files = new ArrayList<>();
        for (int f = 0; f < 20; f++) files.add(new SourceFile("src/f" + f + ".ts", big.toString()));

        var passages = CodeSearcher.search(files, CodeSearcher.parse("cancel reservation"), Set.of(), 4, 3000,
                () -> false);

        assertThat(passages).hasSizeLessThanOrEqualTo(4);
        assertThat(passages.stream().mapToInt(p -> p.text().length()).sum()).isLessThanOrEqualTo(3000);
        assertThat(passages).allSatisfy(p -> assertThat(p.text().lines().mapToInt(String::length).max().orElse(0))
                .isLessThanOrEqualTo(CodeSearcher.MAX_LINE_CHARS));
        assertThat(passages).allSatisfy(p ->
                assertThat(p.lineEnd() - p.lineStart()).isLessThan(CodeSearcher.MAX_MERGED_LINES));
    }

    @Test
    @DisplayName("files of the modules the index points at score higher")
    void preferredModulesWin() {
        var files = List.of(
                new SourceFile("src/legacy/cancel.ts", "export function cancelReservation() {}"),
                new SourceFile("src/booking/cancel.ts", "export function cancelReservation() {}"));

        var passages = CodeSearcher.search(files, CodeSearcher.parse("cancel reservation"),
                Set.of("src/booking"), 5, 4000, () -> false);

        assertThat(passages.getFirst().path()).isEqualTo("src/booking/cancel.ts");
    }

    @Test
    @DisplayName("nothing related returns nothing, and an expired budget stops the scan")
    void nothingRelatedOrExpired() {
        assertThat(CodeSearcher.search(FILES, CodeSearcher.parse("blockchain wallet"), Set.of(), 5, 4000, () -> false))
                .isEmpty();
        assertThat(CodeSearcher.search(FILES, CodeSearcher.parse("cancelar reserva"), Set.of(), 5, 4000, () -> true))
                .isEmpty();
        assertThat(CodeSearcher.search(FILES, CodeSearcher.parse(""), Set.of(), 5, 4000, () -> false)).isEmpty();
    }

    @Test
    @DisplayName("a path that matches lifts a file with a weak content match")
    void pathMatchCounts() {
        var files = List.of(
                new SourceFile("src/misc/helpers.ts", "// cancel once"),
                new SourceFile("src/cancellation/index.ts", "// cancel once"));

        var passages = CodeSearcher.search(files, CodeSearcher.parse("cancel"), Set.of(), 5, 4000, () -> false);

        assertThat(passages.getFirst().path()).isEqualTo("src/cancellation/index.ts");
    }
}
