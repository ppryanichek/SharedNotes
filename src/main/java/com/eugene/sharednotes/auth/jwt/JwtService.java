package com.eugene.sharednotes.auth.jwt;

import com.eugene.sharednotes.auth.dto.GeneratedAccessToken;
import com.eugene.sharednotes.security.SecurityUser;
import com.eugene.sharednotes.user.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey secretKey;

    @Getter
    private final Long expiration;

    public JwtService(@Value("${jwt.secret}") String secretString,
                      @Value("${jwt.access.expiration}") Long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretString));
        this.expiration = expiration;
    }

    /**
     * Генерирует подписанный access-токен для пользователя.
     * @param user пользователь для которого выпускается токен.
     * @return токен вместе со временем истечения.
     */
    public GeneratedAccessToken generateAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expiration);

        String accessToken = Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new GeneratedAccessToken(accessToken, expiresAt);
    }

    /**
     * Извлекает username (subject) из токена
     * @throws JwtException если токен невалиден, просрочен или повреждён.
     */
    public String extractUsername(String accessToken) {
        return extractAllClaims(accessToken).getSubject();
    }

    /**
     * Извлекает role (claim) из токена
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
     * @return false при любой проблеме с токеном
     */
    public boolean isAccessTokenValid(String accessToken, SecurityUser securityUser) {
        if (accessToken == null || accessToken.isBlank()) return false;

        try {
            String username = extractUsername(accessToken);
            return username.equals(securityUser.getUsername());
        } catch (JwtException e) {
            return false;
        }
    }
}
