/**
 * Domain events published by the IAM module for cross-module consumption.
 * <p>
 * Declared as the {@code events} named interface so other modules may subscribe to IAM events via
 * {@code @ApplicationModuleListener} without importing IAM internals. The workspace module consumes
 * {@code AccountVerifiedEvent} here for the invitation link-on-signup safety net.
 */
@org.springframework.modulith.NamedInterface("events")
package com.kntro.reqsai.iam.domain.event;
