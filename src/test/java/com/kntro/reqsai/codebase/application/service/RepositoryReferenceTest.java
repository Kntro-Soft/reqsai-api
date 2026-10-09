package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RepositoryReference")
class RepositoryReferenceTest {

    @ParameterizedTest
    @ValueSource(strings = {"acme/reservas", "https://github.com/acme/reservas", "github.com/acme/reservas.git",
            "https://www.github.com/acme/reservas/", "http://github.com/acme/reservas?tab=readme"})
    @DisplayName("reads owner and name from the short form and from GitHub URLs")
    void parses(String input) {
        RepositoryReference ref = RepositoryReference.parse(input);
        assertThat(ref.owner()).isEqualTo("acme");
        assertThat(ref.name()).isEqualTo("reservas");
        assertThat(ref.branch()).isNull();
    }

    @Test
    @DisplayName("keeps the branch of a /tree/ URL as a hint")
    void branchFromUrl() {
        assertThat(RepositoryReference.parse("https://github.com/acme/reservas/tree/feature/pagos").branch())
                .isEqualTo("feature/pagos");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "acme", "https://gitlab.com/acme/reservas", "acme/reservas/extra", "../etc", "a b/c"})
    @DisplayName("refuses anything that is not a GitHub repository")
    void refuses(String input) {
        assertThatThrownBy(() -> RepositoryReference.parse(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not a GitHub repository");
    }
}
