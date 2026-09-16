package com.eugene.sharednotes.common;

import com.eugene.sharednotes.auth.RefreshTokenRepository;
import com.eugene.sharednotes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CleanupJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final Duration RETENTION_PERIOD = Duration.ofDays(30);

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private CleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        cleanupJob = new CleanupJob(userRepository, refreshTokenRepository, RETENTION_PERIOD, FIXED_CLOCK);
    }

    @Test
    void deleteSoftDeletedUsers_shouldUseRetentionCutoff() {
        cleanupJob.deleteSoftDeletedUsers();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).deleteSoftDeletedBefore(captor.capture());

        assertThat(captor.getValue()).isEqualTo(FIXED_NOW.minus(RETENTION_PERIOD));
    }

    @Test
    void deleteExpiredTokens_shouldDeleteTokens() {
        cleanupJob.deleteExpiredTokens();

        verify(refreshTokenRepository).deleteExpired(FIXED_NOW);
    }
}