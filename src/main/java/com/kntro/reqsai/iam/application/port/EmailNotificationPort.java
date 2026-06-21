package com.kntro.reqsai.iam.application.port;

/**
 * Port for sending transactional email notifications. Implemented in {@code infrastructure} by an
 * {@code EmailRouter} that delegates to the provider selected via {@code EMAIL_PROVIDER}.
 */
public interface EmailNotificationPort {
    void sendVerificationEmail(String toEmail, String firstName, String rawToken);

    void sendPasswordResetEmail(String toEmail, String firstName, String rawToken);
}
