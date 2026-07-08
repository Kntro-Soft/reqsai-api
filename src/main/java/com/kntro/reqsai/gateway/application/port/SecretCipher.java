package com.kntro.reqsai.gateway.application.port;

/**
 * Port for symmetric encryption of integration secrets at rest (ADR-0023).
 * <p>
 * Abstracts the cipher used to protect sensitive credentials (e.g. the Jira API token) before they
 * are persisted, and to recover them on load. Callers program against this port; the concrete
 * algorithm lives in an infrastructure adapter. The stored form is opaque to callers and is
 * self-describing to the adapter that produced it. Implementations must never log plaintext or key
 * material.
 */
public interface SecretCipher {

    /**
     * Encrypts {@code plaintext} into an opaque stored representation.
     *
     * @param plaintext the secret bytes to protect
     * @return the encrypted, self-describing bytes to persist
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * Decrypts bytes previously produced by {@link #encrypt(byte[])} back into plaintext.
     *
     * @param stored the stored, encrypted bytes
     * @return the recovered plaintext bytes
     */
    byte[] decrypt(byte[] stored);
}
