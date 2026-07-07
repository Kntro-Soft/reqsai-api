package com.kntro.reqsai.iam.infrastructure.scheduler;

import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
import com.kntro.reqsai.iam.application.port.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Nightly cleanup of expired IAM tokens.
 * <p>
 * Revoked refresh tokens are not deleted immediately on logout so that a second request with the same
 * token can be detected as a reuse signal (possible session theft). They accumulate until this job runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class IamTokenPurgeScheduler {

    private final EmailVerificationRepository emailVerifications;
    private final RefreshTokenRepository refreshTokens;

    @Scheduled(cron = "${reqsai.iam.purge.cron:0 0 3 * * *}", zone = "UTC")
    @Transactional
    void purgeExpiredTokens() {
        Instant now = Instant.now();
        emailVerifications.deleteExpiredBefore(now);
        refreshTokens.deleteExpiredOrRevokedBefore(now);
        log.info("IAM token purge completed at {}", now);
    }
}
