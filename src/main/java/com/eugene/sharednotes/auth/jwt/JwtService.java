package com.eugene.sharednotes.auth.jwt;

import com.eugene.sharednotes.auth.dto.GeneratedAccessToken;
import com.eugene.sharednotes.common.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey secretKey;

    @Getter
    private final Duration expiration;

    public JwtService(@Value("${jwt.secret}") String secretString,
                      @Value("${jwt.access.expiration}") Duration expiration) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretString));
        this.expiration = expiration;
    }

    /**
     * Генерирует подписанный access-токен для пользователя.
     *
     * @return токен вместе со временем истечения.
     */
    public GeneratedAccessToken generateAccessToken(Long userId, Role role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);

        String accessToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
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
        return Long.valueOf(extractAllClaims(accessToken).getSubject());
    }

    /**
     * Извлекает role (claim) из токена
     *
     * @throws JwtException если токен невалиден, просрочен или повреждён.
     */
    public String extractRole(String accessToken) {
        return extractAllClaims(accessToken).get("role", String.class);
    }

    private Claims extractAllClaims(String accessToken) {
        return Jwts.parser()
                .verifyWith(secretKey)
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
