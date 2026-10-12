package com.kntro.reqsai.codebase.infrastructure.github;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Reads the GitHub App's private key. GitHub hands out a PKCS#1 PEM ({@code BEGIN RSA PRIVATE KEY}), which
 * the JDK cannot read directly, so it is wrapped into PKCS#8; a PKCS#8 PEM is read as is. The value may be
 * the PEM itself, the PEM with {@code \n} escapes (one-line environment variables) or the PEM in base64.
 */
final class PemKeys {

    /** DER of AlgorithmIdentifier { rsaEncryption, NULL }. */
    private static final byte[] RSA_ALGORITHM = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    private PemKeys() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    static PrivateKey rsaPrivateKey(String value) {
        String pem = pemText(value);
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            byte[] pkcs8 = pkcs1 ? wrapPkcs1(der) : der;
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("The GitHub App private key is not a valid RSA PEM key", e);
        }
    }

    private static String pemText(String value) {
        String text = value.strip().replace("\\n", "\n");
        if (text.contains("-----BEGIN")) return text;
        try {
            return new String(Base64.getDecoder().decode(text.replaceAll("\\s", "")), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The GitHub App private key is neither a PEM nor a base64 PEM", e);
        }
    }

    /** PrivateKeyInfo { version 0, rsaEncryption, OCTET STRING pkcs1 }. */
    static byte[] wrapPkcs1(byte[] pkcs1) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[]{0x02, 0x01, 0x00});
        body.writeBytes(RSA_ALGORITHM);
        body.write(0x04);
        body.writeBytes(length(pkcs1.length));
        body.writeBytes(pkcs1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.writeBytes(length(body.size()));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] length(int length) {
        if (length < 0x80) return new byte[]{(byte) length};
        if (length < 0x100) return new byte[]{(byte) 0x81, (byte) length};
        if (length < 0x10000) return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
    }
}
