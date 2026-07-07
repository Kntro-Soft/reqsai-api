package com.kntro.reqsai.integrations.infrastructure.crypto;

import com.kntro.reqsai.integrations.infrastructure.exception.IntegrationsInfrastructureExceptions;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM symmetric encryption for integration secrets at rest (ADR-0022).
 * <p>
 * Each value gets a fresh random 12-byte IV, prepended to the ciphertext+tag so decryption is
 * self-describing: the stored bytes are {@code IV(12) || ciphertext||tag}. The key is a base64-encoded
 * 32-byte value supplied at construction (from {@code INTEGRATIONS_ENCRYPTION_KEY}). Never logs
 * plaintext or key material.
 */
public final class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /** @param base64Key base64-encoded 32-byte (AES-256) key */
    public AesGcmCipher(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw IntegrationsInfrastructureExceptions.encryptionError(
                    "INTEGRATIONS_ENCRYPTION_KEY is not valid base64", e);
        }
        if (raw.length != KEY_LENGTH_BYTES) {
            throw IntegrationsInfrastructureExceptions.encryptionError(
                    "INTEGRATIONS_ENCRYPTION_KEY must decode to 32 bytes (AES-256), got " + raw.length, null);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Encrypts {@code plaintext} → {@code IV || ciphertext+tag}. */
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (Exception e) {
            throw IntegrationsInfrastructureExceptions.encryptionError("encrypt", e);
        }
    }

    /** Decrypts {@code IV || ciphertext+tag} produced by {@link #encrypt(byte[])}. */
    public byte[] decrypt(byte[] stored) {
        try {
            if (stored.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(stored);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw IntegrationsInfrastructureExceptions.encryptionError("decrypt", e);
        }
    }
}
