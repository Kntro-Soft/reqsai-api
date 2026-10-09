package com.kntro.reqsai.codebase.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecretRedactor")
class SecretRedactorTest {

    /** Token-shaped values are built at runtime so the source holds no literal a secret scanner would flag. */
    private static final String STRIPE = "sk_" + "live_" + "abcdefghijklmnopqrstuvwx";
    private static final String GITHUB = "gh" + "p_" + "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String AWS = "AK" + "IA" + "ABCDEFGHIJKLMNOP";

    @Test
    @DisplayName("removes tokens, keys, URL passwords and assigned secrets before the AI reads the code")
    void redacts() {
        String code = """
                const stripe = new Stripe("%s");
                const gh = "%s";
                const aws = "%s";
                const db = "postgres://reservas:SuperSecret123@db.internal:5432/app";
                const config = { apiKey: "abc123def456ghi", password: 'hunter22hunter' };
                -----BEGIN RSA PRIVATE KEY-----
                MIIEpAIBAAKCAQEA
                -----END RSA PRIVATE KEY-----
                export const CANCELLATION_LIMIT_HOURS = 2;
                """.formatted(STRIPE, GITHUB, AWS);
        String out = SecretRedactor.redact(code);
        assertThat(out)
                .doesNotContain(STRIPE, GITHUB, AWS, "SuperSecret123", "abc123def456ghi", "hunter22hunter",
                        "MIIEpAIBAAKCAQEA")
                .contains("postgres://reservas:[REDACTED]@db.internal", "apiKey: \"[REDACTED]\"",
                        "export const CANCELLATION_LIMIT_HOURS = 2;");
    }

    @Test
    @DisplayName("is linear on hostile input")
    void linear() {
        String hostile = "password = \"" + "a".repeat(200_000);
        long started = System.nanoTime();
        SecretRedactor.redact(hostile);
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000);
    }
}
