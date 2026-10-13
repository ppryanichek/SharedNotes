package com.eugene.sharednotes.auth.jwt;

import com.eugene.sharednotes.auth.dto.GeneratedAccessToken;
import com.eugene.sharednotes.common.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

/**
 * Сервис работы с access-токенами: генерация, парсинг, валидация.
 * <p>Subject токена — id пользователя, потому что он стабилен в отличие от username.
 * <p>Время берётся из внедрённого {@link Clock} — используется при генерации
 * и при проверке срока жизни внутри парсера.
 */
@Service
public class JwtService {

    static final String ROLE_CLAIM = "role";

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
    public GeneratedAccessToken generateAccessToken(@NonNull Long userId, @NonNull Role role) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(expiration);

        String accessToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new GeneratedAccessToken(accessToken, expiresAt);
    }

    /**
     * Извлекает userId (subject) из токена
     *
     * @throws JwtException если токен невалиден, просрочен или повреждён.
     * @throws MalformedJwtException если subject отсутствует или не парсится в число.
     */
    public Long extractUserId(String accessToken) {
        String subject = extractAllClaims(accessToken).getSubject();

        if (subject == null)
            throw new MalformedJwtException("Subject is missing");

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("Subject is not correct userId: " + subject, e);
        }
    }

    /**
     * Извлекает role (claim) из токена
     *
     * @throws JwtException если токен невалиден, просрочен или повреждён.
     * @throws MalformedJwtException если claim роли отсутствует или значение не входит в {@link Role}.
     */
    public Role extractRole(String accessToken) {
        String roleName = extractAllClaims(accessToken).get(ROLE_CLAIM, String.class);

        if (roleName == null)
            throw new MalformedJwtException("Role claim is missing");

        try {
            return Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("Role claim is not correct role: " + roleName, e);
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
}
