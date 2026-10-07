package com.kntro.reqsai.discovery.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Discovery: duplicate-story exception")
class DiscoveryExceptionsTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    @DisplayName("similarity is dot-formatted even under a comma-decimal default locale")
    void similarity_dot_formatted_regardless_of_locale() {
        // A comma-decimal locale (es-PE/Germany) would otherwise render "0,87", which the client parses
        // as 0 (0%) off the ProblemDetail detail. Locale.ROOT keeps the machine-parsed value a dot.
        Locale.setDefault(Locale.GERMANY);

        DomainException ex = DiscoveryExceptions.duplicateUserStory(0.87);

        assertThat(ex.getMessage()).contains("similarity 0.87");
        assertThat(ex.getMessage()).doesNotContain("0,87");
    }
}
