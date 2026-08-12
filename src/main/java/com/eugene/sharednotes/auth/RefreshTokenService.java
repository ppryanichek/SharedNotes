package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.exceptions.ExpiredRefreshTokenException;
import com.eugene.sharednotes.exceptions.RevokedTokenException;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * <p>Сервис управления refresh-токенами {@link RefreshToken}.</p>
 * <p>Токены хранятся в БД для возможности детекта кражи.</p>
 * <p>Используется алгоритм SHA-256 для детерминированного поиска токена в БД.</p>
 */

@Slf4j
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    private final Duration expiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${jwt.refresh.expiration}") Duration expiration) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.expiration = expiration;
    }

    /**
     * Генерирует новый токен с помощью метода {@link #generateRandomToken()} и сохраняет его в БД.
     * <p>
     * Собирает токен со следующей информацией:
     *                      <ul>
     *                          <li>{@code userId} id пользователя для которого выпускается токен,</li>
     *                          <li>{@code tokenHash} хэш токена,</li>
     *                          <li>{@code expiresAt} дата истечения токена.</li>
     *                      </ul>
     *
     * @return токен в сыром виде.
     * @throws EntityNotFoundException если пользователь с указанным userId не существует.
     */
    @Transactional
    public String create(Long userId) {
        String token = generateRandomToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(hash(token))
                .expiresAt(Instant.now().plus(expiration))
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    /**
     * Проверяет токен на валидность по следующим критериям:
     *                      <ul>
     *                          <li>{@link RefreshTokenRepository#findByTokenHash(String)} — существует ли токен,</li>
     *                          <li>{@link RefreshToken#isRevoked()} — аннулирован ли токен,</li>
     *                          <li>{@link RefreshToken#getExpiresAt()} — не истёк ли токен.</li>
     *                      </ul>
     *
     * @return полную информацию о токене.
     * @throws BadCredentialsException если токен не найден.
     * @throws RevokedTokenException если предъявлен отозванный токен (переиспользование) — все сессии пользователя отзываются.
     * @throws ExpiredRefreshTokenException если токен истёк.
     */
    private RefreshToken validate(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token!"));

        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUser().getId());
            log.warn("Reuse of revoked refresh token detected for user {}", refreshToken.getUser().getId());
            throw new RevokedTokenException("Token reuse detected, all sessions revoked!");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now()))
            throw new ExpiredRefreshTokenException("Refresh token expired!");

        return refreshToken;
    }

    /**
     * Ротация refresh-токена: инвалидирует старый токен и выдаёт новый.
     * <p>
     * {@code noRollbackFor} нужен, потому что при обнаружении повторного использования
     * отозванного токена отзываются все сессии пользователя и сразу выбрасывается исключение.
     * Без этого Spring откатит транзакцию вместе с отзывом, и сессии останутся активными.
     * <p>
     * Валидация делегируется {@link #validate(String)},
     * откуда пробрасываются исключения при невалидном, отозванном или истёкшем токене.
     *
     * @return {@link RotationResult} новый токен в сыром виде и информацию о пользователе для создания access-токена.
     *
     * @throws BadCredentialsException если токен не найден.
     * @throws RevokedTokenException если предъявлен отозванный токен (переиспользование) — все сессии пользователя отзываются.
     * @throws ExpiredRefreshTokenException если токен истёк.
     */
    @Transactional(noRollbackFor = RevokedTokenException.class)
    public RotationResult rotate(String oldRawToken) {
        RefreshToken oldRefreshToken = validate(oldRawToken);
        oldRefreshToken.setRevoked(true);
        UserEntity user = oldRefreshToken.getUser();
        return new RotationResult(create(user.getId()), user.getId(), user.getRole());
    }

    /**
     * Инвалидирует указанный токен.
     * <p>
     * При отсутствии токена не делает ничего.
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    /**
     * Инвалидирует все токены указанного пользователя.
     */
    @Transactional
    public void revokeAll(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} sessions for user {}", revoked, userId);
    }


    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        }catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
