package com.kntro.reqsai.shared.infrastructure.persistence.auditing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the current user id for JPA auditing ({@code @CreatedBy} / {@code @LastModifiedBy}).
 * <p>
 * {@code @EnableJpaAuditing} lives on the application class; this provides the {@link AuditorAware}.
 * The principal is the user id set by {@link
 * com.kntro.reqsai.shared.infrastructure.security.JwtAuthenticationFilter}. Returns empty for
 * anonymous/system operations (e.g. startup, provisioning), so audit columns stay null.
 */
@Configuration
public class JpaAuditingConfiguration {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(Objects.requireNonNull(auth.getPrincipal()).toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        };
    }
}
