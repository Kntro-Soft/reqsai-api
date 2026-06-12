package com.kntro.reqsai.shared.infrastructure.web.error;

import com.kntro.reqsai.shared.domain.exception.AuthenticationException;
import com.kntro.reqsai.shared.domain.exception.CommonError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Central exception handling, producing RFC 9457 {@link ProblemDetail} responses.
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC exceptions (unreadable body, method
 * not allowed, unsupported media type, …) are already rendered as {@code ProblemDetail}. Domain
 * exceptions map to their error catalog's HTTP status. Every response carries a {@code code} and
 * a {@code correlationId} (from the MDC, set by {@code CorrelationFilter}). Infrastructure errors
 * never leak their internal message to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Domain
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        log.warn("[{}] Not found [{}]: {}", tenantId(), ex.error().code(), ex.getMessage());
        return problem(ex, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex, HttpServletRequest req) {
        log.warn("[{}] Auth failed [{}]: {}", tenantId(), ex.error().code(), ex.getMessage());
        return problem(ex, req);
    }

    @ExceptionHandler(InfrastructureException.class)
    public ProblemDetail handleInfra(InfrastructureException ex, HttpServletRequest req) {
        // Internal cause is logged with stacktrace, never exposed to the client.
        log.error("[{}] Infrastructure error [{}]: {}", tenantId(), ex.error().code(), ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(ex.error().status());
        pd.setDetail("A server error occurred. Please try again later.");
        pd.setProperty("code", ex.error().code());
        pd.setProperty("correlationId", correlationId());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest req) {
        log.warn("[{}] Domain error [{}]: {}", tenantId(), ex.error().code(), ex.getMessage());
        return problem(ex, req);
    }

    // Validation (@Valid) — override the base handler to enrich the body
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage()),
                        "rejectedValue", fe.getRejectedValue() != null ? fe.getRejectedValue() : "null"))
                .toList();

        log.warn("[{}] Validation failed on {}: {} error(s)", tenantId(), uri(request), fieldErrors.size());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Request validation failed");
        pd.setProperty("code", CommonError.VALIDATION_FAILED.code());
        pd.setProperty("fieldErrors", fieldErrors);
        pd.setProperty("correlationId", correlationId());
        pd.setInstance(URI.create(uri(request)));
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Fallback
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception ex, HttpServletRequest req) {
        log.error("[{}] Unhandled exception on {}", tenantId(), req.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setDetail("An unexpected error occurred");
        pd.setProperty("code", CommonError.INTERNAL_ERROR.code());
        pd.setProperty("correlationId", correlationId());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    // Helpers
    private ProblemDetail problem(DomainException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.error().status(), ex.getMessage());
        pd.setProperty("code", ex.error().code());
        pd.setProperty("correlationId", correlationId());
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    private String uri(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
    }

    private String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("unknown");
    }

    private String tenantId() {
        return Optional.ofNullable(MDC.get("tenantId")).orElse("system");
    }
}
