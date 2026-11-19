package com.kntro.reqsai.discovery.infrastructure.ai.improvement;

import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Discovery: story improvement reply parser")
class StoryImprovementParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REPLY = """
            {"proposal": {"title": "Exportar el backlog", "role": "analista", "action": "exportar a CSV",
              "benefit": "compartirlo", "acceptanceCriteria": [
                {"scenario": "feliz", "given": "hay historias", "when": "exporta",
                 "then": "responde en menos de [[tiempo máximo]] segundos"}]},
             "pendingValues": [{"token": "[[tiempo máximo]]", "hint": "¿Cuántos segundos?"}],
             "fixes": ["Se reemplazó «rápido»", ""]}""";

    @Test
    @DisplayName("reads the proposal, the pending values and the fixes")
    void reads_the_contract() {
        StoryImprovementResult result = StoryImprovementParser.parse(MAPPER, REPLY);

        assertThat(result.proposal().title()).isEqualTo("Exportar el backlog");
        assertThat(result.proposal().acceptanceCriteria()).hasSize(1);
        assertThat(result.proposal().acceptanceCriteria().getFirst().scenario()).isEqualTo("feliz");
        assertThat(result.pendingValues()).containsExactly(
                new StoryImprovementResult.PendingValue("[[tiempo máximo]]", "¿Cuántos segundos?"));
        assertThat(result.fixes()).containsExactly("Se reemplazó «rápido»");
    }

    @Test
    @DisplayName("tolerates code fences, prose around the object and a proposal given at the root")
    void tolerates_wrapping() {
        String flat = "Claro:\n```json\n{\"title\": \"T\", \"role\": \"r\", \"action\": \"a\", \"benefit\": \"b\"}\n```";

        StoryImprovementResult result = StoryImprovementParser.parse(MAPPER, flat);

        assertThat(result.proposal().title()).isEqualTo("T");
        assertThat(result.proposal().acceptanceCriteria()).isEmpty();
        assertThat(result.pendingValues()).isEmpty();
    }

    @Test
    @DisplayName("a pending value given as a bare string is read, with no hint")
    void bare_string_pending_value() {
        StoryImprovementResult result = StoryImprovementParser.parse(MAPPER,
                "{\"proposal\": {\"title\": \"T\"}, \"pendingValues\": [\"[[x]]\"]}");

        assertThat(result.pendingValues()).containsExactly(new StoryImprovementResult.PendingValue("[[x]]", ""));
    }

    @Test
    @DisplayName("anything that is not the contract is an UnparseableGenerationException")
    void rejects_what_is_not_the_contract() {
        for (String reply : new String[]{"", "Lo siento, no puedo.", "[1, 2]", "{\"respuesta\": \"hola\"}",
                "{\"proposal\": {\"title\": \"T\", \"acceptanceCriteria\": [\"texto\"]}}", "{ \"proposal\": {\"title\": "}) {
            assertThatThrownBy(() -> StoryImprovementParser.parse(MAPPER, reply))
                    .as(reply).isInstanceOf(UnparseableGenerationException.class);
        }
    }
}
