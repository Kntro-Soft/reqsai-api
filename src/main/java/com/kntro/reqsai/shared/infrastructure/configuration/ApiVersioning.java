package com.kntro.reqsai.shared.infrastructure.configuration;

/**
 * API versioning constants.
 * <p>
 * Uses Spring Framework 7 / Spring Boot 4 native, <strong>header-based</strong> API versioning: the
 * base path stays clean ({@code /api/...}) and clients select the version with the {@code Api-Version}
 * header. Each endpoint declares its version via the {@code version} attribute of the mapping.
 *
 * <pre>{@code
 * @RequestMapping(path = ApiVersioning.BASE + "/organizations")
 * public interface OrganizationController {
 *     @PostMapping(version = ApiVersioning.V1)
 *     ResponseEntity<OrganizationResponse> create(...);
 * }
 * }</pre>
 *
 * Client: {@code POST /api/organizations} with header {@code Api-Version: 1} (omitting it falls back to
 * the default version). Benefits over a path version: clean URLs, per-endpoint granularity, and
 * multiple versions coexisting in one controller. Wired in {@link ApiVersioningConfig}.
 */
public final class ApiVersioning {

    /** Base path for all REST endpoints. */
    public static final String BASE = "/api";

    /** Version 1. */
    public static final String V1 = "1";

    /** Version 2 (reserved for future use). */
    public static final String V2 = "2";

    /** Header carrying the requested API version. */
    public static final String HEADER = "Api-Version";

    private ApiVersioning() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
