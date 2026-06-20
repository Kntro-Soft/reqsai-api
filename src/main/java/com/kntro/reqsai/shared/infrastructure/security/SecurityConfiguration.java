package com.kntro.reqsai.shared.infrastructure.security;

import com.kntro.reqsai.shared.domain.exception.CommonError;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.shared.infrastructure.web.CorrelationFilter;
import com.kntro.reqsai.shared.infrastructure.web.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless HTTP security.
 * <p>
 * No server session (JWT only), CSRF disabled (no cookies), CORS driven by {@link CorsProperties}.
 * {@link CorrelationFilter} then {@link JwtAuthenticationFilter} run before the username/password
 * filter. The filters are instantiated here and added to the security chain directly (not exposed as
 * beans), so the servlet container never registers/initializes them as standalone filters.
 * {@link EnableMethodSecurity} enables {@code @PreAuthorize} in the BCs.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/ws/**"
    };

    private final TokenVerifier tokenVerifier;
    private final TenantSchemaResolver tenantSchemaResolver;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        CorrelationFilter correlationFilter = new CorrelationFilter();
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(tokenVerifier, tenantSchemaResolver);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthenticatedEntryPoint()))
                .addFilterBefore(correlationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, CorrelationFilter.class);
        return http.build();
    }

    /**
     * Returns 401 (not 403) when a request reaches a protected endpoint without valid credentials.
     * Spring Security's default sends 403 via AccessDeniedHandler even for unauthenticated requests;
     * RFC 9110 §15.5.2 requires 401 when authentication is required but missing or invalid.
     */
    private AuthenticationEntryPoint unauthenticatedEntryPoint() {
        var mapper = new ObjectMapper();
        return (request, response, ex) -> {
            CommonError error = CommonError.NOT_AUTHENTICATED;
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(error.status(), "Authentication required");
            pd.setProperty("code", error.code());
            pd.setInstance(java.net.URI.create(request.getRequestURI()));
            response.setStatus(error.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), pd);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(corsProperties.allowedMethods());
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        config.setAllowCredentials(corsProperties.allowCredentials());
        config.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
