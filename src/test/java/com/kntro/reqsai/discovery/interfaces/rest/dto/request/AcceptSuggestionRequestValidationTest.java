package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-validation tests for {@link AcceptSuggestionRequest}: the edited payload is validated with the
 * same constraints as story/criterion creation, so an edit cannot commit a value the backlog rejects.
 */
@DisplayName("Interfaces: AcceptSuggestionRequest validation")
class AcceptSuggestionRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("an empty body (no edits) is valid")
    void empty_body_is_valid() {
        assertThat(validator.validate(new AcceptSuggestionRequest(
                null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("a fully edited payload with valid criteria is valid")
    void valid_edited_payload() {
        var request = new AcceptSuggestionRequest(
                "Title", "role", "action", "benefit", Priority.HIGH, 3,
                List.of(new AcceptSuggestionRequest.EditedCriterion("label", "given", "when", "then")));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("an edited criterion missing 'then' is rejected (cascaded @NotBlank)")
    void criterion_missing_then_is_rejected() {
        var request = new AcceptSuggestionRequest(
                null, null, null, null, null, null,
                List.of(new AcceptSuggestionRequest.EditedCriterion("label", "given", "when", "  ")));

        Set<ConstraintViolation<AcceptSuggestionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().endsWith("then"));
    }
}
