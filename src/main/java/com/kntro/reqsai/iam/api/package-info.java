/**
 * Public API of the IAM module — integration events and interfaces exposed to other modules.
 * <p>
 * Declared as the {@code api} named interface so other bounded contexts may subscribe to IAM
 * integration events (e.g. {@link com.kntro.reqsai.iam.api.AccountVerifiedIntegrationEvent}) via
 * {@code @ApplicationModuleListener} without importing IAM internals. Internal domain events stay in
 * {@code iam.domain.event}; IAM relays the ones meant for cross-module consumption here, keeping the
 * domain layer framework-agnostic (no Modulith annotations in {@code ..domain..}).
 */
@org.springframework.modulith.NamedInterface("api")
package com.kntro.reqsai.iam.api;
