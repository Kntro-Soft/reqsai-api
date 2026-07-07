package com.kntro.reqsai.gateway.infrastructure.crypto;

import com.kntro.reqsai.gateway.application.port.SecretCipher;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import com.kntro.reqsai.gateway.infrastructure.persistence.converters.EncryptedStringConverter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AES-256-GCM cipher used to encrypt integration secrets at rest (ADR-0022) and injects it
 * into the Hibernate-instantiated {@link EncryptedStringConverter} via its static holder.
 * <p>
 * The key comes from {@code INTEGRATIONS_ENCRYPTION_KEY} (base64, 32 bytes). It is required for the
 * integrations feature; if absent the context fails fast at startup with a clear message rather than
 * only when a token is first persisted.
 */
@Configuration
@Slf4j
public class IntegrationsCryptoConfiguration {

    private final SecretCipher cipher;

    public IntegrationsCryptoConfiguration(@Value("${reqsai.integrations.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw IntegrationsInfrastructureExceptions.encryptionError(
                    "INTEGRATIONS_ENCRYPTION_KEY is not configured", null);
        }
        this.cipher = new AesGcmCipher(base64Key);
    }

    @Bean
    SecretCipher integrationsCipher() {
        return cipher;
    }

    @PostConstruct
    void wireConverter() {
        EncryptedStringConverter.setCipher(cipher);
        log.info("Integrations secret encryption initialized (AES-256-GCM)");
    }
}
