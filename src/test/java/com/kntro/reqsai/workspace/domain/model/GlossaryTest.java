package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
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
        @DisplayName("should add term successfully")
        void should_add_term_successfully() {
            Glossary glossary = new Glossary(UUID.randomUUID());

            GlossaryTerm term = glossary.addTerm("Lead", "Potential customer", UUID.randomUUID());

            assertThat(glossary.getTerms()).containsExactly(term);
            assertThat(term.getTerm()).isEqualTo("Lead");
            assertThat(term.getDefinition()).isEqualTo("Potential customer");
        }

        @Test
        @DisplayName("should reject duplicate term ignoring case and surrounding spaces")
        void should_reject_duplicate_term_ignoring_case_and_surrounding_spaces() {
            Glossary glossary = new Glossary(UUID.randomUUID());
            glossary.addTerm("Lead", "Potential customer", UUID.randomUUID());

            assertThatThrownBy(() -> glossary.addTerm("  lead  ", "Another definition", UUID.randomUUID()))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("should reject blank term")
        void should_reject_blank_term() {
            Glossary glossary = new Glossary(UUID.randomUUID());

            assertThatThrownBy(() -> glossary.addTerm("   ", "Potential customer", UUID.randomUUID()))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("should reject blank definition")
        void should_reject_blank_definition() {
            Glossary glossary = new Glossary(UUID.randomUUID());

            assertThatThrownBy(() -> glossary.addTerm("Lead", "   ", UUID.randomUUID()))
                    .isInstanceOf(DomainException.class);
        }
    }
}
