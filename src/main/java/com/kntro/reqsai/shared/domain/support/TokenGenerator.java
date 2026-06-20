package com.kntro.reqsai.shared.domain.support;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Pure cryptographic utility for generating secure random tokens.
 * <p>
 * {@link SecureRandom} is JDK-provided with no I/O or external dependencies, so this belongs
 * alongside {@link HashUtils} in the shared domain support layer. Multiple handlers that need
 * one-time tokens (email verification, password reset) delegate here instead of each owning
 * their own {@code SecureRandom} instance and duplicating the hex-encoding logic.
 * <p>
 * The {@code SecureRandom} instance is shared and thread-safe.
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Generates a cryptographically secure random token encoded as a lowercase hex string.
     *
     * @param bytes number of random bytes to generate (e.g. {@code 32} → 64-char hex string)
     * @return lowercase hex string of length {@code bytes * 2}
     */
    public static String generate(int bytes) {
        byte[] raw = new byte[bytes];
        RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }
}
