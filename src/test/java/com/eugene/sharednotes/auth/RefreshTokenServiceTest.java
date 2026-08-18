package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.common.Tokens;
import com.eugene.sharednotes.exceptions.ExpiredRefreshTokenException;
import com.eugene.sharednotes.exceptions.RevokedTokenException;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Тесты {@link RefreshTokenService}.
 * <p>Время фиксируется через {@link Clock}, поэтому тесты детерминированы.
 * Хеширование не мокается — {@link Tokens} статический, поэтому
 * {@code TOKEN_HASH} вычисляется по-настоящему.
 */
@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    private static final String RAW_TOKEN = "raw-token";

    // Хеш вычисляется настоящим Tokens.hash — сервис посчитает такой же.
    private static final String TOKEN_HASH = Tokens.hash(RAW_TOKEN);

    private static final Duration DEFAULT_EXPIRATION = Duration.ofDays(30);
    private static final Long USER_ID = 1L;

    // Момент в котором застыли тесты. Значение произвольное — важна фиксированность.
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);


    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenService refreshTokenService;

    private UserEntity existingUser;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                DEFAULT_EXPIRATION,
                FIXED_CLOCK
        );

        existingUser = UserEntity.builder().id(USER_ID).build();

        refreshToken = RefreshToken.builder()
                .user(existingUser)
                .tokenHash(TOKEN_HASH)
                .expiresAt(FIXED_NOW.plus(DEFAULT_EXPIRATION))
                .revoked(false)
                .build();
    }

    @Test
    void revokeAll_shouldRevokeAllUserSessions() {
        refreshTokenService.revokeAll(USER_ID);

        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Nested
    class Revoke {

        @Test
        void shouldRevokeToken_whenTokenExists() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));

            refreshTokenService.revoke(RAW_TOKEN);

            assertThat(refreshToken.isRevoked()).isTrue();
        }

        @Test
        void shouldDoNothing_whenTokenDoesNotExist() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> refreshTokenService.revoke(RAW_TOKEN));
        }
    }

    @Nested
    class Create {

        @Test
        void shouldSetExpiresAtToNowPlusExpiration() {
            when(userRepository.getReferenceById(USER_ID)).thenReturn(existingUser);

            refreshTokenService.create(USER_ID);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());


            assertThat(captor.getValue().getExpiresAt()).isEqualTo(FIXED_NOW.plus(DEFAULT_EXPIRATION));
        }

        @Test
        void shouldSaveHashMatchingReturnedRawToken() {
            when(userRepository.getReferenceById(USER_ID)).thenReturn(existingUser);

            String result = refreshTokenService.create(USER_ID);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            assertThat(captor.getValue().getTokenHash()).isEqualTo(Tokens.hash(result));
        }
    }

    @Nested
    class Rotate {

        @Test
        void shouldThrowBadCredentialsException_whenTokenDoesNotExist() {

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        void shouldThrowRevokedTokenException_whenTokenIsRevoked() {
            refreshToken.setRevoked(true);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(refreshToken));

            assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                    .isInstanceOf(RevokedTokenException.class);
            verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
        }

        @Test
        void shouldThrowExpiredRefreshTokenException_whenTokenExpired() {
            RefreshToken expiredToken = RefreshToken.builder()
                    .user(existingUser)
                    .tokenHash(TOKEN_HASH)
                    .expiresAt(FIXED_NOW.minus(Duration.ofDays(40)))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                    .isInstanceOf(ExpiredRefreshTokenException.class);
        }

        @Test
        void shouldReturnRotationResultWithCorrectUserId() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(refreshToken));
            when(userRepository.getReferenceById(USER_ID)).thenReturn(existingUser);

            RotationResult result = refreshTokenService.rotate(RAW_TOKEN);

            assertThat(result.userId()).isEqualTo(USER_ID);
        }

        @Test
        void shouldRevokeOldToken_whenRotationSucceeds() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(refreshToken));
            when(userRepository.getReferenceById(USER_ID)).thenReturn(existingUser);

            refreshTokenService.rotate(RAW_TOKEN);

            assertThat(refreshToken.isRevoked()).isTrue();
        }

        @Test
        void shouldSaveNewToken_whenRotationSucceeds() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(refreshToken));
            when(userRepository.getReferenceById(USER_ID)).thenReturn(existingUser);

            RotationResult result = refreshTokenService.rotate(RAW_TOKEN);

            assertThat(result.rawRefreshToken()).isNotBlank();
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }
    }
}
