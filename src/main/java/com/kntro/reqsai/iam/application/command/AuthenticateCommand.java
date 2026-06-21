package com.kntro.reqsai.iam.application.command;

/**
 * Intent to authenticate with email + password and obtain an access token.
 *
 * @param email    account email
 * @param password clear-text password to verify against the stored hash
 */
public record AuthenticateCommand(String email, String password) {
}
