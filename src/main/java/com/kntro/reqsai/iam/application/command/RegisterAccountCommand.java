package com.kntro.reqsai.iam.application.command;

/**
 * Intent to register a new account and its user profile.
 *
 * @param email     account email (validated/normalized into an {@code Email} by the handler)
 * @param password  clear-text password (hashed by the handler before persistence; never stored as-is)
 * @param firstName user's first name
 * @param lastName  user's last name
 */
public record RegisterAccountCommand(
        String email,
        String password,
        String firstName,
        String lastName
) {
}
