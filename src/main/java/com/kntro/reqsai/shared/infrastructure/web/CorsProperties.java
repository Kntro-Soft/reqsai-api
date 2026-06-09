package com.kntro.reqsai.shared.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized CORS configuration, bound from {@code reqsai.cors.*}.
 * <p>
 * Keeps allowed origins out of code so each environment (dev Angular at localhost:4200, prod domain)
 * sets its own via {@code application-<profile>.yml} or env vars.
 *
 * @param allowedOrigins   permitted origins (exact, no wildcard when credentials are allowed)
 * @param allowedMethods   permitted HTTP methods
 * @param allowedHeaders   permitted request headers
 * @param allowCredentials whether cookies / Authorization may be sent cross-origin
 * @param maxAge           preflight cache duration, in seconds
 */
@ConfigurationProperties(prefix = "reqsai.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        long maxAge
) {
    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            allowedHeaders = List.of("*");
        }
        if (maxAge <= 0) {
            maxAge = 3600L;
        }
    }
}
