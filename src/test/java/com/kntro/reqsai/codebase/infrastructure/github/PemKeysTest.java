package com.kntro.reqsai.codebase.infrastructure.github;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PemKeys")
class PemKeysTest {

    private static final KeyPair KEY = generate();

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    @Test
    @DisplayName("reads the PKCS#1 PEM GitHub hands out, also as one line with \\n escapes or in base64")
    void pkcs1() {
        byte[] pkcs8 = KEY.getPrivate().getEncoded();
        String pkcs1 = pem("RSA PRIVATE KEY", Arrays.copyOfRange(pkcs8, 26, pkcs8.length));

        assertThat(PemKeys.rsaPrivateKey(pkcs1).getEncoded()).isEqualTo(pkcs8);
        assertThat(PemKeys.rsaPrivateKey(pkcs1.replace("\n", "\\n")).getEncoded()).isEqualTo(pkcs8);
        String base64 = Base64.getEncoder().encodeToString(pkcs1.getBytes(StandardCharsets.US_ASCII));
        assertThat(PemKeys.rsaPrivateKey(base64).getEncoded()).isEqualTo(pkcs8);
    }

    @Test
    @DisplayName("reads a PKCS#8 PEM as is, and refuses what is not a key")
    void pkcs8() {
        PrivateKey key = PemKeys.rsaPrivateKey(pem("PRIVATE KEY", KEY.getPrivate().getEncoded()));
        assertThat(key.getEncoded()).isEqualTo(KEY.getPrivate().getEncoded());
        assertThatThrownBy(() -> PemKeys.rsaPrivateKey("-----BEGIN PRIVATE KEY-----\nbm8=\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class);
    }
}
