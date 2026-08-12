package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.auth.dto.*;
import com.eugene.sharednotes.auth.jwt.JwtService;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.exceptions.*;
import com.eugene.sharednotes.security.SecurityUser;
import com.eugene.sharednotes.user.UserService;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import com.eugene.sharednotes.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления сессией пользователя.
 * <p>
 * Предоставляет такие методы как:
 *                      <ul>
 *                          <li>{@code register}    регистрация пользователя,</li>
 *                          <li>{@code login}       аутентификация пользователя,</li>
 *                          <li>{@code logout}      закрытие текущей сессии,</li>
 *                          <li>{@code logoutAll}   закрытие всех сессий пользователя,</li>
 *                          <li>{@code refresh}     выдача новых токенов.</li>
 *                      </ul>
 */

@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Создаёт нового пользователя через {@link CreateUserRequest}.
     * <p>
     * Устанавливает роль USER по умолчанию.
     *
     * @param request поля для регистрации пользователя:
     *                      <ul>
     *                          <li>{@code username} имя пользователя,</li>
     *                          <li>{@code password} пароль пользователя (в открытом виде),</li>
     *                          <li>{@code email}    email пользователя.</li>
     *                      </ul>
     * @return {@link TokenPairResponse} новую пару (refresh и access) токенов
     * созданных через {@link #issueTokens(Long, Role)}.
     * @throws UserAlreadyExistsException если пользователь с данным email или username уже существует.
     */
    public TokenPairResponse register(RegisterRequest request) {
        Role defaultRole = Role.USER;
        UserResponse userResponse = userService.createUser(new CreateUserRequest(
                request.username(),
                request.password(),
                request.email(),
                defaultRole
        ));
        return issueTokens(userResponse.id(), userResponse.role());
    }

    /**
     * Проверяет аутентификацию пользователя по переданным полям.
     *
     * @param request поля для аутентификации пользователя:
     *                      <ul>
     *                          <li>{@code email}    email пользователя,</li>
     *                          <li>{@code password} пароль пользователя (в открытом виде),</li>
     *                      </ul>
     *
     * @return {@link TokenPairResponse} новую пару (refresh и access) токенов
     * созданных через {@link #issueTokens(Long, Role)}.
     *
     * @throws BadCredentialsException если email не найден или неверный пароль.
     */
    public TokenPairResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        SecurityUser principal = (SecurityUser) auth.getPrincipal();
        return issueTokens(principal.getUserId(), principal.getRole());
    }

    /**
     * Меняет пароль пользователя и перевыпускает сессию.
     * <p>
     * Все текущие сессии пользователя отзываются, взамен выдаётся новая пара токенов.
     *
     * @param request старый и новый пароли в открытом виде.
     *
     * @return {@link TokenPairResponse} новую пару (refresh и access) токенов
     *
     * @throws UserNotFoundException    если пользователя с данным id не найден.
     * @throws InvalidPasswordException если текущий пароль неверный.
     */
    @Transactional
    public TokenPairResponse changePassword(Long userId, Role role, ChangePasswordRequest request) {
        userService.changePassword(userId, request.oldPassword(), request.newPassword());
        refreshTokenService.revokeAll(userId);
        return issueTokens(userId, role);
    }

    /**
     * Инвалидирует указанный токен.
     * <p>
     * При отсутствии токена не делает ничего.
     */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Инвалидирует все токены указанного пользователя.
     */
    public void logoutAll(Long userId) {
        refreshTokenService.revokeAll(userId);
    }

    /**
     * Ротирует refresh-токен: инвалидирует старый и выдаёт новую пару токенов.
     * <p>
     * Ротация делегируется {@link RefreshTokenService#rotate(String)}.
     *
     * @return {@link TokenPairResponse} новую пару (refresh и access) токенов.
     * @throws BadCredentialsException если токен не найден.
     * @throws RevokedTokenException если предъявлен отозванный токен (переиспользование) — все сессии пользователя отзываются.
     * @throws ExpiredRefreshTokenException если токен истёк.
     */
    public TokenPairResponse refresh(String refreshToken) {
        RotationResult result = refreshTokenService.rotate(refreshToken);
        GeneratedAccessToken access = jwtService.generateAccessToken(result.userId(), result.role());
        return new TokenPairResponse(access.token(), result.rawRefreshToken());
    }

    /**
     * Генерирует токены.
     * @return новую пару токенов в сыром виде.
     */
    private TokenPairResponse issueTokens(Long userId, Role role) {
        GeneratedAccessToken access = jwtService.generateAccessToken(userId, role);
        String refreshToken = refreshTokenService.create(userId);
        return new TokenPairResponse(access.token(), refreshToken);
    }
}