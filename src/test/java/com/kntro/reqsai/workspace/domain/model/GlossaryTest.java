package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.mothers.GlossaryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: Glossary Aggregate")
class GlossaryTest {

    @Nested
    @DisplayName("Add term")
    class AddTerm {

        @Test
        @DisplayName("should add a term with given and definition")
        void should_add_term() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();

            GlossaryTerm term = glossary.addTerm("Sprint", "Fixed-length iteration in Scrum.");

            assertThat(term.getTerm()).isEqualTo("Sprint");
            assertThat(term.getDefinition()).isEqualTo("Fixed-length iteration in Scrum.");
            assertThat(term.getEmbedding()).isNull();
            assertThat(glossary.getTerms()).hasSize(1);
        }

        @Test
        @DisplayName("should reject blank term name")
        void should_reject_blank_term() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            assertThatThrownBy(() -> glossary.addTerm("  ", "Valid definition."))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("should reject blank definition")
        void should_reject_blank_definition() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            assertThatThrownBy(() -> glossary.addTerm("Sprint", "  "))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("Update term")
    class UpdateTerm {

        @Test
        @DisplayName("should update term fields and clear embedding")
        void should_update_term_and_clear_embedding() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            GlossaryTerm term = glossary.addTerm("Sprint", "Old definition.");
            term.applyEmbedding(new float[768]);

            glossary.updateTerm(term.getId(), "Sprint v2", "New definition.");

            assertThat(term.getTerm()).isEqualTo("Sprint v2");
            assertThat(term.getDefinition()).isEqualTo("New definition.");
            assertThat(term.getEmbedding()).isNull();
        }

        @Test
        @DisplayName("should throw when term does not exist")
        void should_throw_when_term_not_found() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            assertThatThrownBy(() -> glossary.updateTerm(UUID.randomUUID(), "X", "Y"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Remove term")
    class RemoveTerm {

        @Test
        @DisplayName("should remove an existing term")
        void should_remove_term() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            GlossaryTerm term = glossary.addTerm("Sprint", "A time-box.");

            glossary.removeTerm(term.getId());

            assertThat(glossary.getTerms()).isEmpty();
        }

        @Test
        @DisplayName("should be a no-op when term does not exist")
        void should_be_noop_when_not_found() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            glossary.addTerm("Sprint", "A time-box.");

            glossary.removeTerm(UUID.randomUUID());

            assertThat(glossary.getTerms()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Apply embedding")
    class ApplyEmbedding {

        @Test
        @DisplayName("should store the embedding on the term")
        void should_apply_embedding() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            GlossaryTerm term = glossary.addTerm("Sprint", "A time-box.");
            float[] vector = new float[768];
            vector[0] = 0.5f;

            glossary.applyTermEmbedding(term.getId(), vector);

            assertThat(term.getEmbedding()).isNotNull();
            assertThat(term.getEmbedding()[0]).isEqualTo(0.5f);
        }

        @Test
        @DisplayName("should throw when term does not exist")
        void should_throw_when_term_not_found() {
            Glossary glossary = GlossaryBuilder.aGlossary().build();
            assertThatThrownBy(() -> glossary.applyTermEmbedding(UUID.randomUUID(), new float[768]))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
