/**
 * Shared Kernel — cross-cutting building blocks reused by every bounded context.
 * <p>
 * Declared as an {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN} module so any
 * bounded context (iam, billing, workspace, discovery, gateway) may depend on it without an explicit
 * {@code allowedDependencies} declaration.
 * <p>
 * Contains:
 * <ul>
 *   <li>{@code domain.model} — base aggregate root (UUID v7 identity, auditing, opt-in soft-delete, domain events)</li>
 *   <li>{@code domain.exception} — {@code ErrorCatalog} interface + {@code CommonError} (generic codes) + minimal exception hierarchy + cross-cutting {@code Exceptions} factory</li>
 *   <li>{@code domain.valueobjects} — reusable value objects (e.g. Email)</li>
 *   <li>{@code domain.support} — domain utilities ({@code IdGenerator}, {@code Assert})</li>
 *   <li>{@code application.notification} — {@code RealtimeNotifier} port; bounded contexts push live updates
 *       through it without touching messaging infrastructure</li>
 *   <li>{@code interfaces.pagination} — transport-agnostic paging/sorting contracts ({@code PageResponse},
 *       {@code PageCriteria}, {@code SortPolicy})</li>
 *   <li>{@code infrastructure} — multitenancy (schema-per-tenant), security (JWT verification), persistence
 *       auditing &amp; query specifications, caching, web error handling (RFC 9457), OpenAPI, and the
 *       STOMP-over-WebSocket realtime adapter (switchable SIMPLE/RELAY broker)</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.kntro.reqsai.shared;
