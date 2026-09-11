package com.eugene.sharednotes.common;

import com.eugene.sharednotes.auth.RefreshTokenRepository;
import com.eugene.sharednotes.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class CleanupJob {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration retentionPeriod;
    private final Clock clock;

    public CleanupJob(UserRepository userRepository,
               RefreshTokenRepository refreshTokenRepository,
               @Value("${account.retention-period}") Duration retentionPeriod,
               Clock clock) {
        this.userRepository= userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.retentionPeriod = retentionPeriod;
        this.clock = clock;
    }

    @Scheduled( cron = "0 0 2 * * *")
    @Transactional
    public void deleteSoftDeletedUsers() {
        Instant cutoff = Instant.now(clock).minus(retentionPeriod);
        int deleted = userRepository.deleteSoftDeletedBefore(cutoff);
        log.info("Deleted {} soft-deleted users", deleted);
    }

    @Scheduled( cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpired(Instant.now(clock));
        log.info("Deleted {} expired refresh tokens", deleted);
    }

}