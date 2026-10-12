package com.kntro.reqsai.discovery.infrastructure.ai.verification;

import com.kntro.reqsai.codebase.api.CodeSnippet;
import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Discovery: deep verification verdict parsing")
class CodeVerificationParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SHA = "abc1234";
    private static final List<CodeSnippet> SNIPPETS = List.of(
            new CodeSnippet("acme/reservas", SHA, "src/reservations/cancellation.policy.ts", 10, 14,
                    "export const CANCELLATION_LIMIT_HOURS = 2;\nexport function canCancel() {}\n\n// x\n// y",
                    "https://github.com/acme/reservas/blob/" + SHA + "/src/reservations/cancellation.policy.ts#L10-L14", 9.0),
            new CodeSnippet("acme/reservas", SHA, "README.md", 1, 3, "# Reservas\nSistema\nOnline",
                    "https://github.com/acme/reservas/blob/" + SHA + "/README.md#L1-L3", 2.0));

    private static CodeVerification parse(String reply) {
        return CodeVerificationParser.parse(JSON, reply, SNIPPETS);
    }

    @Test
    @DisplayName("a conflict keeps its note and a reference with file, lines, quote and a GitHub link")
    void conflictWithReference() {
        CodeVerification v = parse("""
                {"verdict":"CONFLICTS_WITH_CODE","note":"El código permite cancelar hasta 2 h antes; el cliente pide 24 h",
                 "references":[{"snippet":"S1","lineStart":10,"lineEnd":11,"quote":"CANCELLATION_LIMIT_HOURS = 2"}]}
                """);

        assertThat(v.verdict()).isEqualTo(CodeVerdict.CONFLICTS_WITH_CODE);
        assertThat(v.note()).contains("2 h antes");
        CodeReference ref = v.references().getFirst();
        assertThat(ref.repository()).isEqualTo("acme/reservas");
        assertThat(ref.file()).isEqualTo("src/reservations/cancellation.policy.ts");
        assertThat(ref.path()).isEqualTo("src/reservations");
        assertThat(ref.name()).isEqualTo("cancellation.policy.ts");
        assertThat(ref.lineStart()).isEqualTo(10);
        assertThat(ref.lineEnd()).isEqualTo(11);
        assertThat(ref.quote()).isEqualTo("CANCELLATION_LIMIT_HOURS = 2");
        assertThat(ref.url()).isEqualTo(
                "https://github.com/acme/reservas/blob/abc1234/src/reservations/cancellation.policy.ts#L10-L11");
        assertThat(ref.hasLines()).isTrue();
    }

    @Test
    @DisplayName("lines outside the passage are clamped, reversed lines are put in order, a made-up quote is dropped")
    void clampsLinesAndChecksQuotes() {
        CodeVerification v = parse("""
                {"verdict":"ALREADY_EXISTS","note":"ok",
                 "references":[{"snippet":"s1","lineStart":99,"lineEnd":2,"quote":"this is not in the code"}]}
                """);

        CodeReference ref = v.references().getFirst();
        assertThat(ref.lineStart()).isEqualTo(10);
        assertThat(ref.lineEnd()).isEqualTo(14);
        assertThat(ref.quote()).isNull();
        assertThat(ref.url()).endsWith("#L10-L14");
    }

    @Test
    @DisplayName("a reference to a passage the model was not shown is dropped, and the claim becomes UNSURE")
    void unknownSnippetIsNotBelieved() {
        CodeVerification v = parse("""
                {"verdict":"ALREADY_EXISTS","note":"it does","references":[{"snippet":"S9","lineStart":1,"lineEnd":2}]}
                """);

        assertThat(v.verdict()).isEqualTo(CodeVerdict.UNSURE);
        assertThat(v.references()).isEmpty();
        assertThat(v.note()).isEqualTo("it does");
    }

    @Test
    @DisplayName("NOT_FOUND and UNSURE need no reference; NOT_FOUND never carries one")
    void verdictsWithoutReferences() {
        assertThat(parse("{\"verdict\":\"UNSURE\",\"note\":\"partial\"}").verdict()).isEqualTo(CodeVerdict.UNSURE);
        CodeVerification notFound = parse("""
                {"verdict":"not_found","note":"nothing","references":[{"snippet":"S1","lineStart":10,"lineEnd":10}]}
                """);
        assertThat(notFound.verdict()).isEqualTo(CodeVerdict.NOT_FOUND);
        assertThat(notFound.references()).isEmpty();
    }

    @Test
    @DisplayName("duplicates are merged, at most five references are kept, a file name works without a key")
    void dedupesAndCaps() {
        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < 8; i++) refs.append("{\"snippet\":\"S1\",\"lineStart\":10,\"lineEnd\":10},");
        refs.append("{\"file\":\"README.md\",\"lineStart\":2,\"lineEnd\":2}");

        CodeVerification v = parse("{\"verdict\":\"ALREADY_EXISTS\",\"note\":\"n\",\"references\":[" + refs + "]}");

        assertThat(v.references()).hasSize(2);
        assertThat(v.references().get(1).file()).isEqualTo("README.md");
    }

    @Test
    @DisplayName("a reply wrapped in Markdown fences is read; prose or an unknown verdict is rejected")
    void tolerantButStrict() {
        assertThat(parse("```json\n{\"verdict\":\"UNSURE\",\"note\":\"x\"}\n```").verdict())
                .isEqualTo(CodeVerdict.UNSURE);
        assertThatThrownBy(() -> parse("Sorry, I cannot")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parse("{\"verdict\":\"MAYBE\"}")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a long note is cut")
    void capsNote() {
        CodeVerification v = parse("{\"verdict\":\"UNSURE\",\"note\":\"" + "a".repeat(2000) + "\"}");
        assertThat(v.note()).hasSize(CodeVerificationParser.NOTE_MAX).endsWith("...");
    }
}
