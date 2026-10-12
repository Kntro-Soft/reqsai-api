package com.kntro.reqsai.workspace.insights;

import java.util.Map;

/**
 * The organization's people.
 *
 * @param active
 *            owner plus active members
 * @param pending
 *            invited members who have not joined yet
 * @param activeByRole
 *            active people per organization role ({@code OWNER}, {@code ADMIN},
 *            {@code MEMBER}), every role present
 */
public record MemberHeadcount(long active, long pending, Map<String, Long> activeByRole) {
}
