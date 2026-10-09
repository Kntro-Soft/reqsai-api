package com.kntro.reqsai.codebase.infrastructure.crypto;

import com.kntro.reqsai.codebase.application.port.TokenCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption of repository access tokens at rest, with the same key as the other
 * integration secrets ({@code INTEGRATIONS_ENCRYPTION_KEY}). Stored form: {@code IV(12) || ciphertext+tag}.
 * The key is read lazily, so a deployment without private repositories needs no key; never logs
 * plaintext or key material.
 */
@Component
class AesGcmTokenCipher implements TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final String base64Key;
    private final SecureRandom random = new SecureRandom();

    AesGcmTokenCipher(@Value("${reqsai.integrations.encryption-key:}") String base64Key) {
        this.base64Key = base64Key == null ? "" : base64Key.strip();
    }

    @Override
    public byte[] encrypt(String token) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the repository access token", e);
        }
    }

    @Override
    public String decrypt(byte[] stored) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(stored);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt the repository access token", e);
        }
    }

    private SecretKeySpec key() {
        if (base64Key.isEmpty()) {
            throw new IllegalStateException("INTEGRATIONS_ENCRYPTION_KEY is not configured");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("INTEGRATIONS_ENCRYPTION_KEY must decode to 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
