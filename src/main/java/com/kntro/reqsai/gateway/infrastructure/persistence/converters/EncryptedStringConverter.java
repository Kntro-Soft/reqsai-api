package com.kntro.reqsai.gateway.infrastructure.persistence.converters;

import com.kntro.reqsai.gateway.application.port.SecretCipher;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * JPA converter that encrypts a {@code String} attribute (the Jira API token) to a {@code byte[]}
 * ({@code secret_ciphertext} BYTEA) with AES-256-GCM and decrypts it on load (ADR-0022).
 * <p>
 * JPA converters are instantiated by Hibernate, not Spring, so the {@link SecretCipher} is supplied
 * through a static holder set once at startup by {@code IntegrationsCryptoConfiguration}. A missing
 * cipher (no key configured) surfaces as {@code INTEGRATION_ENCRYPTION_ERROR} rather than a null token.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    private static volatile @Nullable SecretCipher cipher;

    /** Wired once at startup by the crypto configuration. */
    public static void setCipher(SecretCipher secretCipher) {
        cipher = secretCipher;
    }

    @Override
    public byte @Nullable [] convertToDatabaseColumn(@Nullable String attribute) {
        if (attribute == null) {
            return null;
        }
        return cipher().encrypt(attribute.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public @Nullable String convertToEntityAttribute(byte @Nullable [] dbData) {
        if (dbData == null) {
            return null;
        }
        return new String(cipher().decrypt(dbData), StandardCharsets.UTF_8);
    }

    private static SecretCipher cipher() {
        SecretCipher c = cipher;
        if (c == null) {
            throw IntegrationsInfrastructureExceptions.encryptionError(
                    "encryption cipher is not configured (INTEGRATIONS_ENCRYPTION_KEY missing)", null);
        }
        return c;
    }
}
