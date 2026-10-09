package com.kntro.reqsai.codebase.application.port;

/** Encrypts the access token of a private repository at rest, and recovers it to read the repository. */
public interface TokenCipher {

    byte[] encrypt(String token);

    String decrypt(byte[] ciphertext);
}
