package com.kntro.reqsai.iam.application.result;

import com.kntro.reqsai.iam.domain.model.User;

/**
 * A user's profile together with its account email. The email lives on the IAM {@code Account} aggregate
 * (not on the {@code User}); this carrier pairs the two so the interfaces layer can surface the email in
 * {@code UserResponse} without crossing the aggregate boundary.
 *
 * @param user  the user profile aggregate
 * @param email the account email associated with the user
 */
public record UserProfile(User user, String email) {
}
