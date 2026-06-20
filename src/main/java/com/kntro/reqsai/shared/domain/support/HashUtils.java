package com.kntro.reqsai.shared.domain.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Pure cryptographic utility for the shared domain kernel.
 * <p>
 * SHA-256 is a pure, deterministic function with no I/O or external dependencies, so it belongs
 * in the shared domain support layer alongside {@link Assert} and {@link IdGenerator}.
 * Domain aggregates delegate here instead of inlining the algorithm, keeping the implementation
 * detail in one place and the domain model free of cryptographic code.
 */
public final class HashUtils {

    private HashUtils() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Returns the lowercase hex SHA-256 digest of the given string (UTF-8 encoded).
     *
     * @param raw the plain-text value to hash (never stored; only the hash is persisted)
     * @return 64-character lowercase hex string
     * @throws IllegalStateException if the JVM doesn't provide SHA-256 (should never happen)
     */
    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
