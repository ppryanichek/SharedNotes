package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.auth.exceptions.ExpiredRefreshTokenException;
import com.eugene.sharednotes.auth.exceptions.RevokedTokenException;
import com.eugene.sharednotes.common.Tokens;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Сервис управления refresh-токенами {@link RefreshToken}.
 * <p>Токены хранятся в БД для возможности детекта кражи.
 * <p>Используется алгоритм SHA-256 для детерминированного поиска токена в БД.
 * <p>Время берётся из внедрённого {@link Clock}: срок жизни и проверка
 * на истечение считаются от одного источника.
 */

@Slf4j
@Service
public class RefreshTokenService {


    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    private final Duration expiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${jwt.refresh.expiration}") Duration expiration,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.expiration = expiration;
        this.clock = clock;
    }

    /**
     * Генерирует новый токен с помощью метода {@link Tokens#generateRandomToken()} и сохраняет его в БД.
     * <p>Собирает токен со следующей информацией:
     *                      <ul>
     *                          <li>{@code userId} id пользователя для которого выпускается токен,</li>
     *                          <li>{@code tokenHash} хэш токена,</li>
     *                          <li>{@code expiresAt} дата истечения токена.</li>
     *                      </ul>
     *
     * @return токен в сыром виде.
     */
    @Transactional
    public String create(Long userId) {
        String token = Tokens.generateRandomToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(Tokens.hash(token))
                .expiresAt(Instant.now(clock).plus(expiration))
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
                .findByTokenHash(Tokens.hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token!"));

        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUser().getId());
            log.warn("Reuse of revoked refresh token detected for user {}", refreshToken.getUser().getId());
            throw new RevokedTokenException();
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now(clock)))
            throw new ExpiredRefreshTokenException();

        return refreshToken;
    }

    /**
     * Ротация refresh-токена: инвалидирует старый токен и выдаёт новый.
     * <p>{@code noRollbackFor} нужен, потому что при обнаружении повторного использования
     * отозванного токена отзываются все сессии пользователя и сразу выбрасывается исключение.
     * Без этого Spring откатит транзакцию вместе с отзывом, и сессии останутся активными.
     * <p>Валидация делегируется {@link #validate(String)},
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
        return new RotationResult(create(user.getId()), user.getId());
    }

    /**
     * Инвалидирует указанный токен.
     * <p>При отсутствии токена не делает ничего.
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(Tokens.hash(rawToken))
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
}
