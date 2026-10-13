package com.eugene.sharednotes.auth.jwt;

import com.eugene.sharednotes.auth.dto.GeneratedAccessToken;
import com.eugene.sharednotes.common.Role;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString(Jwts.SIG.HS256.key().build().getEncoded());

    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));

    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(15);

    private static final Long USER_ID = 42L;

    private static final Role USER_ROLE = Role.USER;

    // Момент в котором застыли тесты. Значение произвольное — важна фиксированность.
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, DEFAULT_EXPIRATION, FIXED_CLOCK);
    }

    // Собирает токен вручную, минуя сервис — чтобы задать любой subject и claim
    private String tokenWithSubject(String subject, String claim) {
        Instant now = Instant.now(FIXED_CLOCK);
        return Jwts.builder()
                .subject(subject)
                .claim(JwtService.ROLE_CLAIM, claim)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(DEFAULT_EXPIRATION)))
                .signWith(SECRET_KEY)
                .compact();
    }

    @Nested
    class GenerateAccessToken {
        @Test
        void shouldPutUserIdIntoSubject() {
            GeneratedAccessToken access = jwtService.generateAccessToken(USER_ID, USER_ROLE);

            assertThat(jwtService.extractUserId(access.token())).isEqualTo(USER_ID);
        }

        @Test
        void shouldPutRoleIntoClaim() {
            GeneratedAccessToken access = jwtService.generateAccessToken(USER_ID, USER_ROLE);

            assertThat(jwtService.extractRole(access.token())).isEqualTo(USER_ROLE);
        }

        @Test
        void shouldSetExpiresAtToNowPlusTtl() {
            GeneratedAccessToken access = jwtService.generateAccessToken(USER_ID, USER_ROLE);

            assertThat(access.expiresAt()).isEqualTo(FIXED_NOW.plus(DEFAULT_EXPIRATION));
        }

        @Test
        void shouldThrowNPE_whenUserIdIsNull() {
            assertThatThrownBy(() -> jwtService.generateAccessToken(null, USER_ROLE))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldThrowNPE_whenRoleClaimIsNull() {
            assertThatThrownBy(() -> jwtService.generateAccessToken(USER_ID, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ExtractUserId {

        private static final String ROLE_NAME = "USER";

        @Test
        void shouldThrowJwtException_whenSubjectIsNotNumeric() {
            String token = tokenWithSubject("abc", ROLE_NAME);

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
            String token = tokenWithSubject(String.valueOf(USER_ID), ROLE_NAME);

            String fakedToken = token + "1";
            assertThatThrownBy(() -> jwtService.extractUserId(fakedToken))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Nested
    class ExtractRole {

        private static final String SUBJECT = String.valueOf(USER_ID);

        @Test
        void shouldReturnRole_whenExtractionSucceeds() {
            String token = tokenWithSubject(SUBJECT, "ADMIN");

            assertThat(jwtService.extractRole(token)).isEqualTo(Role.ADMIN);
        }

        @Test
        void shouldThrowJwtException_whenClaimIsMissing() {
            String token = tokenWithSubject(SUBJECT, null);

            assertThatThrownBy(() -> jwtService.extractRole(token))
                    .isInstanceOf(JwtException.class)
                    .hasMessage("Role claim is missing");
        }

        @Test
        void shouldThrowJwtException_whenClaimIsNotEnum() {
            String token = tokenWithSubject(SUBJECT, "SUPER_ADMIN");

            assertThatThrownBy(() -> jwtService.extractRole(token))
                    .isInstanceOf(JwtException.class);
        }
    }
}
