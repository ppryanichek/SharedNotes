package com.eugene.sharednotes.auth.jwt;

import com.eugene.sharednotes.auth.dto.GeneratedAccessToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Сервис работы с access-токенами: генерация, парсинг, валидация.
 * <p>Subject токена — id пользователя, потому что он стабилен в отличие от username.
 * <p>Время берётся из внедрённого {@link Clock} — используется при генерации
 * и при проверке срока жизни внутри парсера.
 */
@Service
public class JwtService {
    private final SecretKey secretKey;
    private final Clock clock;

    @Getter
    private final Duration expiration;

    public JwtService(@Value("${jwt.secret}") String secretString,
                      @Value("${jwt.access.expiration}") Duration expiration,
                      Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretString));
        this.expiration = expiration;
        this.clock = clock;
    }

    /**
     * Генерирует подписанный access-токен для пользователя.
     *
     * @return токен вместе со временем истечения.
     */
    public GeneratedAccessToken generateAccessToken(Long userId) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(expiration);

        String accessToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new GeneratedAccessToken(accessToken, expiresAt);
    }

    /**
     * Извлекает username (subject) из токена
     *
     * @throws JwtException если токен невалиден, просрочен или повреждён.
     */
    public Long extractUserId(String accessToken) {
        String subject = extractAllClaims(accessToken).getSubject();

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("Subject не является корректным userId: " + subject, e);
        }
    }

    // JJWT сверяет со своими часами — подставляем те же что при генерации.
    private Claims extractAllClaims(String accessToken) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
    }

    /**
     * Проверяет, что токен валиден, не просрочен и принадлежит указанному пользователю.
     * В отличие от extract-методов, не выбрасывает исключение при проблемах с токеном —
     * любая ошибка приводит к false.
     *
     * @return false при любой проблеме с токеном
     */
    public boolean isAccessTokenValid(String accessToken, Long expectedUserId) {
        if (accessToken == null || accessToken.isBlank()) return false;

        try {
            Long userId = extractUserId(accessToken);
            return userId.equals(expectedUserId);
        } catch (JwtException e) {
            return false;
        }
    }
}
