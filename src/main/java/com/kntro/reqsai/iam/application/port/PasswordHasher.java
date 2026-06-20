package com.kntro.reqsai.iam.application.port;

/**
 * Password hashing port — keeps the application/domain layers free of any concrete crypto library.
 * Implemented in {@code infrastructure} (BCrypt).
 */
public interface PasswordHasher {

    /** Hashes a clear-text password for storage. */
    String hash(String rawPassword);

    /** Verifies a clear-text password against a previously stored hash. */
    boolean matches(String rawPassword, String passwordHash);
}
