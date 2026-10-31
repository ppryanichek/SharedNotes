package com.eugene.sharednotes.user;

import com.eugene.sharednotes.auth.RefreshTokenService;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import com.eugene.sharednotes.user.dto.UserResponse;
import com.eugene.sharednotes.user.dto.UserUpdateRequest;
import com.eugene.sharednotes.user.exceptions.InvalidPasswordException;
import com.eugene.sharednotes.user.exceptions.RestoreWindowExpiredException;
import com.eugene.sharednotes.user.exceptions.UserAlreadyExistsException;
import com.eugene.sharednotes.user.exceptions.UserNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;


/**
 * Сервис управления пользователями: CRUD-операции над {@link UserEntity}
 * <p>Все методы работают только с активными пользователями: мягко удалённые
 * пользователи исключаются автоматически через {@link UserEntity} ({@code @SQLRestriction}).
 * <p>Использует {@link Clock} для проверки истечения окна восстановления пользователя.
 */
@Transactional(readOnly = true)
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final Duration retentionPeriod;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            @Value("${account.retention-period}") Duration retentionPeriod,
            Clock clock) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.retentionPeriod = retentionPeriod;
        this.clock = clock;
    }

    /**
     * Возвращает постраничный список всех пользователей.
     *
     * @param pageable параметры пагинации:
     *                 <ul>
     *                     <li>{@code page} — номер (индекс) страницы,</li>
     *                     <li>{@code size} — размер страницы,</li>
     *                     <li>{@code sort} — параметры сортировки.</li>
     *                 </ul>
     * @return постраничное отображение информации о пользователях (за исключением паролей).
     */
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    /**
     * Возвращает пользователя c указанным id.
     *
     * @return информацию о пользователе (за исключением пароля).
     * @throws UserNotFoundException если пользователь с данным id не найден.
     */
    public UserResponse findById(Long id) {
        return UserResponse.from(getUserOrThrow(id));
    }

    /**
     * Возвращает пользователя c указанным username.
     *
     * @return информацию о пользователе (за исключением пароля).
     * @throws UserNotFoundException если пользователь с данным username не найден.
     */
    public UserResponse findByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        return UserResponse.from(user);
    }

    /**
     * Создаёт нового пользователя с указанными полями.
     * <p>
     * Проверяет существование пользователя по полям email и username.
     * <p>
     * Хеширует пароль перед сохранением в БД.
     *
     * @param request валидированные поля для создания пользователя:
     *                <ul>
     *                    <li>{@code username} имя пользователя,</li>
     *                    <li>{@code password} пароль пользователя (в открытом виде),</li>
     *                    <li>{@code email}    email пользователя,</li>
     *                    <li>{@code role}     роль пользователя.</li>
     *                </ul>
     * @return информацию о созданном пользователе (за исключением пароля).
     * @throws UserAlreadyExistsException если пользователь с данным email или username уже существует.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIncludingSoftDeleted(request.email()))
            throw new UserAlreadyExistsException("User already exists with this email: " + request.email());

        if (userRepository.existsByUsernameIncludingSoftDeleted(request.username()))
            throw new UserAlreadyExistsException("User already exists with this username: " + request.username());

        UserEntity newUser = UserEntity.builder()
                .username(request.username())
                .role(request.role() != null ? request.role() : Role.USER)
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .build();

        userRepository.save(newUser);

        return UserResponse.from(newUser);
    }

    /**
     * Изменяет поля существующего пользователя (за исключением пароля).
     * <p>
     * {@code null}-поля игнорируются, а не затирают значение.
     *
     * @param request поля, которые необходимо изменить.
     * @return новую информацию о пользователе (за исключением пароля).
     * @throws UserNotFoundException      если пользователь с данным id не найден.
     * @throws UserAlreadyExistsException если новый username или email уже занят другим пользователем.
     */
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        UserEntity updateUser = getUserOrThrow(id);

        if (request.username() != null && !request.username().isBlank()) {
            if (!request.username().equals(updateUser.getUsername())
                    && userRepository.existsByUsernameIncludingSoftDeleted(request.username()))
                throw new UserAlreadyExistsException("User already exists with this username: " + request.username());

            updateUser.setUsername(request.username());
        }

        if (request.email() != null && !request.email().isBlank()) {
            if (!request.email().equals(updateUser.getEmail())
                    && userRepository.existsByEmailIncludingSoftDeleted(request.email()))
                throw new UserAlreadyExistsException("User already exists with this email: " + request.email());

            updateUser.setEmail(request.email());
        }

        return UserResponse.from(updateUser);
    }

    /**
     * Меняет старый пароль указанного пользователя на новый.
     * <p>
     * Новый пароль хешируется перед сохранением.
     *
     * @throws UserNotFoundException    если пользователь с данным id не найден.
     * @throws InvalidPasswordException если текущий пароль неверный.
     */
    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        UserEntity userEntity = getUserOrThrow(id);

        if (!passwordEncoder.matches(oldPassword, userEntity.getPassword()))
            throw new InvalidPasswordException("Current password is incorrect");

        userEntity.setPassword(passwordEncoder.encode(newPassword));
    }

    /**
     * Удаляет существующего пользователя (soft delete).
     * <p>
     * Ищет указанного пользователя в системе и устанавливает {@code deletedAt} на текущее время удаления.
     * <p>
     * Завершает все текущие сессии пользователя.
     *
     * @throws UserNotFoundException если пользователь с данным id не найден.
     */
    @Transactional
    public void deleteUser(Long id) {
        UserEntity userEntity = getUserOrThrow(id);

        userEntity.setDeletedAt(Instant.now(clock));
        refreshTokenService.revokeAll(id);
    }

    /**
     * Снимает метку удаления с soft-deleted пользователя,
     * если окно восстановления ещё активно.
     * <p>Поиск идёт нативным запросом для обхода {@code @SQLRestriction}.
     *
     * @return id пользователя.
     * @throws UserNotFoundException         если удалённого пользователя с данным email не существует.
     * @throws BadCredentialsException       если пароль неверный.
     * @throws RestoreWindowExpiredException если окно восстановления истекло.
     */
    @Transactional
    public UserResponse restore(String email, String password) {
        UserEntity userEntity = userRepository.findSoftDeletedByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (!passwordEncoder.matches(password, userEntity.getPassword()))
            throw new BadCredentialsException("Invalid email or password");

        Instant cutoff = Instant.now(clock).minus(retentionPeriod);
        if (userEntity.getDeletedAt().isBefore(cutoff))
            throw new RestoreWindowExpiredException("Account restoration period has expired");

        userEntity.setDeletedAt(null);

        return UserResponse.from(userEntity);
    }

    public boolean adminExists() {
        return userRepository.existsByRole(Role.ADMIN);
    }

    private UserEntity getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }
}
