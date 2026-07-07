package com.kntro.reqsai.integrations.infrastructure.crypto;

import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Infrastructure: AES-256-GCM cipher")
class AesGcmCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("encrypt then decrypt round-trips the plaintext")
    void round_trips() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        byte[] plaintext = "super-secret-jira-token".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = cipher.encrypt(plaintext);
        byte[] decrypted = cipher.decrypt(encrypted);

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("super-secret-jira-token");
        assertThat(encrypted).isNotEqualTo(plaintext);
        // IV(12) is prepended, so ciphertext is longer than plaintext.
        assertThat(encrypted.length).isGreaterThan(plaintext.length + 12);
    }

    @Test
    @DisplayName("uses a fresh IV per value (same input yields different ciphertext)")
    void fresh_iv_per_value() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        byte[] plaintext = "token".getBytes(StandardCharsets.UTF_8);

        assertThat(cipher.encrypt(plaintext)).isNotEqualTo(cipher.encrypt(plaintext));
    }

    @Test
    @DisplayName("rejects a key that does not decode to 32 bytes")
    void rejects_wrong_key_length() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmCipher(shortKey))
                .isInstanceOf(InfrastructureException.class);
    }

    @Test
    @DisplayName("fails to decrypt tampered ciphertext (GCM auth tag)")
    void detects_tampering() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        byte[] encrypted = cipher.encrypt("token".getBytes(StandardCharsets.UTF_8));
        encrypted[encrypted.length - 1] ^= 0x01; // flip a bit in the tag

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(InfrastructureException.class);
    }
}
