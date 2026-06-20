/**
 * Ports exposed by the IAM module for cross-module access.
 * <p>
 * {@code OrganizationLookupPort} lives here so the workspace BC can implement it
 * without taking a full dependency on the IAM internal application layer.
 * Declared as a named interface so Spring Modulith enforces the boundary.
 */
@org.springframework.modulith.NamedInterface("ports")
package com.kntro.reqsai.iam.application.port;
