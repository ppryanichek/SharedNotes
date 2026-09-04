package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.RepositoryTestBase;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RefreshTokenRepositoryTest extends RepositoryTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration EXPIRATION = Duration.ofDays(30);
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity user;

    private UserEntity persistUser(String username, String email) {
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .password("hashed")
                .role(Role.USER)
                .build();

        entityManager.persist(user);
        entityManager.flush();

        return user;
    }

    private RefreshToken persistToken(UserEntity owner, String hash, boolean revoked, Instant expiresAt) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(owner)
                .tokenHash(hash)
                .revoked(revoked)
                .expiresAt(expiresAt)
                .build();

        entityManager.persist(refreshToken);
        entityManager.flush();

        return refreshToken;
    }

    @BeforeEach
    void setUp() {
        user = persistUser("Bob", "bob@mail.ru");
    }

    @Nested
    class RevokeAllByUserId {

        @Test
        void shouldRevokeAllActiveToken_andReturnAmount() {
            persistToken(user, "own-hash", false, FIXED_NOW.plus(EXPIRATION));

            int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
            entityManager.clear();

            assertThat(revoked).isEqualTo(1);
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::getTokenHash, RefreshToken::isRevoked)
                    .containsExactly(tuple("own-hash", true));

        }

        @Test
        void shouldNotCountAlreadyRevokedTokens() {
            UserEntity otherUser = persistUser("Other", "other@mail.ru");
            persistToken(user, "own-hash", false, FIXED_NOW.plus(EXPIRATION));
            persistToken(otherUser, "other-hash", false, FIXED_NOW.plus(EXPIRATION));

            int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
            entityManager.clear();

            assertThat(revoked).isEqualTo(1);
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::getTokenHash, RefreshToken::isRevoked)
                    .containsExactlyInAnyOrder(
                            tuple("own-hash", true),
                            tuple("other-hash", false));
        }

        @Test
        void shouldRevokedOnlyActiveTokens() {
            persistToken(user, "revoked-hash", true, FIXED_NOW.plus(EXPIRATION));
            persistToken(user, "active-hash", false, FIXED_NOW.plus(EXPIRATION));

            int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
            entityManager.clear();

            assertThat(revoked).isEqualTo(1);
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::getTokenHash, RefreshToken::isRevoked)
                    .containsExactlyInAnyOrder(
                            tuple("active-hash", true),
                            tuple("revoked-hash", true));
        }

        @Test
        void shouldReturnZero_whenNoActiveTokens() {
            persistToken(user, "revoked-hash-1", true, FIXED_NOW.plus(EXPIRATION));
            persistToken(user, "revoked-hash-2", true, FIXED_NOW.plus(EXPIRATION));

            int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
            entityManager.clear();

            assertThat(revoked).isZero();
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::isRevoked)
                    .containsOnly(true);
        }
    }

    @Nested
    class DeleteExpired {

        @Test
        void shouldDeleteAndReturnAmoundOfExpiredTokens() {
            persistToken(user, "expired-hash", false, FIXED_NOW.minus(Duration.ofDays(1)));
            persistToken(user, "active-hash", false, FIXED_NOW.plus(Duration.ofDays(1)));

            int deleted = refreshTokenRepository.deleteExpired(FIXED_NOW);
            entityManager.clear();

            assertThat(deleted).isEqualTo(1);
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::getTokenHash)
                    .containsExactly("active-hash");
        }

        @Test
        void shouldReturnZero_whenNothingExpired() {
            persistToken(user, "active-hash-1", false, FIXED_NOW.plus(Duration.ofDays(1)));
            persistToken(user, "active-hash-2", false, FIXED_NOW.plus(Duration.ofDays(1)));

            int deleted = refreshTokenRepository.deleteExpired(FIXED_NOW);
            entityManager.clear();

            assertThat(deleted).isZero();
            assertThat(refreshTokenRepository.findAll())
                    .extracting(RefreshToken::getTokenHash)
                    .containsExactlyInAnyOrder("active-hash-1", "active-hash-2");
        }
    }

}