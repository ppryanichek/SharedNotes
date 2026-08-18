package com.eugene.sharednotes.auth.jwt;

import com.eugene.sharednotes.auth.dto.GeneratedAccessToken;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты {@link JwtService}.
 * <p>Время фиксируется через {@link Clock}, поэтому проверки срока жизни токена
 * детерминированы и не зависят от даты запуска.
 */
public class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString(Jwts.SIG.HS256.key().build().getEncoded());

    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));

    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(15);
    private static final Long USER_ID = 42L;

    // Момент в котором застыли тесты. Значение произвольное — важна фиксированность.
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, DEFAULT_EXPIRATION, FIXED_CLOCK);
    }

    // Собирает токен вручную, минуя сервис — чтобы задать любой subject
    private String tokenWithSubject(String subject, SecretKey key) {
        Instant now = Instant.now(FIXED_CLOCK);
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(JwtServiceTest.DEFAULT_EXPIRATION)))
                .signWith(key)
                .compact();
    }

    @Nested
    class GenerateAccessToken {

        @Test
        void shouldPutUserIdIntoSubject() {
            GeneratedAccessToken access = jwtService.generateAccessToken(USER_ID);

            assertThat(jwtService.extractUserId(access.token())).isEqualTo(USER_ID);
        }

        @Test
        void shouldSetExpiresAtToNowPlusTtl() {
            GeneratedAccessToken access = jwtService.generateAccessToken(USER_ID);

            assertThat(access.expiresAt()).isEqualTo(FIXED_NOW.plus(DEFAULT_EXPIRATION));
        }
    }

    @Nested
    class ExtractUserId {
        @Test
        void shouldThrowJwtException_whenSubjectIsNotNumeric() {
            String token = tokenWithSubject("abc", SECRET_KEY);

            assertThatThrownBy(() -> jwtService.extractUserId(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        void shouldThrowJwtException_whenTokenNotJwt() {
            assertThatThrownBy(() -> jwtService.extractUserId("not-a-token"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        void shouldThrowJwtException_whenTokenIsFake() {
            String token = tokenWithSubject(String.valueOf(USER_ID), SECRET_KEY);

            String fakedToken = token + "1";
            assertThatThrownBy(() -> jwtService.extractUserId(fakedToken))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Nested
    class IsAccessTokenValid {
        @Test
        void shouldReturnFalse_whenSubjectIsNotNumeric() {
            String token = tokenWithSubject("abc", SECRET_KEY);

            assertThat(jwtService.isAccessTokenValid(token, USER_ID)).isFalse();
        }

        @Test
        void shouldReturnTrue_whenTokenIsFreshAndUserMatches() {
            String token = tokenWithSubject(
                    String.valueOf(USER_ID),
                    SECRET_KEY
            );

            assertThat(jwtService.isAccessTokenValid(token, USER_ID)).isTrue();
        }

        @Test
        void shouldReturnFalse_whenUserIdDoesNotMatch() {
            String token = tokenWithSubject("12", SECRET_KEY);

            assertThat(jwtService.isAccessTokenValid(token, USER_ID)).isFalse();
        }

        @Test
        void shouldReturnFalse_whenSignedWithForeignKey() {
            SecretKey foreignKey = Jwts.SIG.HS256.key().build();
            String token = tokenWithSubject(
                    String.valueOf(USER_ID),
                    foreignKey
            );

            assertThat(jwtService.isAccessTokenValid(token, USER_ID)).isFalse();
        }

        @Test
        void shouldReturnFalse_whenTokenIsExpired() {
            // Сервис с часами на час раньше — токен родится уже истёкшим.
            Clock pastClock = Clock.fixed(FIXED_NOW.minus(Duration.ofHours(1)),ZoneOffset.UTC);
            JwtService pastJwtService = new JwtService(SECRET, DEFAULT_EXPIRATION, pastClock);

            String expiredToken = pastJwtService.generateAccessToken(USER_ID).token();

            assertThat(jwtService.isAccessTokenValid(expiredToken, USER_ID)).isFalse();
        }

        @Test
        void shouldReturnTrue_whenTokenIsNotYetExpired() {
            Clock recentClock = Clock.fixed(FIXED_NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC);

            JwtService recentJwtService = new JwtService(SECRET, DEFAULT_EXPIRATION, recentClock);

            String token = recentJwtService.generateAccessToken(USER_ID).token();

            assertThat(jwtService.isAccessTokenValid(token, USER_ID)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void shouldReturnFalse_whenTokenIsNullOrBlank(String token) {
            assertThat(jwtService.isAccessTokenValid(token, USER_ID)).isFalse();
        }
    }
}
