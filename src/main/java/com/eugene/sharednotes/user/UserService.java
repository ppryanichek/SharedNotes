package com.eugene.sharednotes.user;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import com.eugene.sharednotes.user.dto.UserResponse;
import com.eugene.sharednotes.exceptions.UserAlreadyExistsException;
import com.eugene.sharednotes.exceptions.UserNotFoundException;
import com.eugene.sharednotes.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Сервис управления пользователями: CRUD-операции над {@link UserEntity}
 */
@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Возвращает постраничный список всех пользователей без фильтрации.
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
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return UserResponse.from(user);

    }

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
     * @param request валидированные поля для создания пользователя:
     *                      <ul>
     *                          <li>{@code username} имя пользователя,</li>
     *                          <li>{@code password} пароль пользователя (в открытом виде),</li>
     *                          <li>{@code email}    email пользователя,</li>
     *                          <li>{@code role}     роль пользователя.</li>
     *                      </ul>
     * @return информацию о созданном пользователе (за исключением пароля).
     * @throws UserAlreadyExistsException если пользователь с данным email или username уже существует.
     */
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email()))
            throw new UserAlreadyExistsException("User already exists with this email: " + request.email());

        if (userRepository.existsByUsername(request.username()))
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
     * Проверяет какие поля в запросе ненулевые и применяет изменения в случае несовпадения.
     *
     * @param request поля, которые необходимо изменить.
     * @return новую информацию о пользователе (за исключением пароля).
     * @throws UserNotFoundException      если пользователя с данным id не найден.
     * @throws UserAlreadyExistsException если новый username или email уже занят другим пользователем.
     */
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        UserEntity updateUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (request.username() != null && !request.username().isBlank()) {
            if (!request.username().equals(updateUser.getUsername())
                    && userRepository.existsByUsername(request.username()))
                throw new UserAlreadyExistsException("User already exists with this username: " + request.username());

            updateUser.setUsername(request.username());
        }

        if (request.email() != null && !request.email().isBlank()) {
            if (!request.email().equals(updateUser.getEmail())
                    && userRepository.existsByEmail(request.email()))
                throw new UserAlreadyExistsException("User already exists with this email: " + request.email());

            updateUser.setEmail(request.email());
        }

        return UserResponse.from(updateUser);
    }

    /**
     * Удаляет существующего пользователя.
     *
     * @throws UserNotFoundException если пользователь с данным id не найден.
     */
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id))
            throw new UserNotFoundException("User not found with id: " + id);

        userRepository.deleteById(id);
    }
}
