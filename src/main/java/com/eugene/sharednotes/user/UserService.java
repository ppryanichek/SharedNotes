package com.eugene.sharednotes.user;

import com.eugene.sharednotes.user.dto.CreateUserDTO;
import com.eugene.sharednotes.user.dto.UserResponseDTO;
import com.eugene.sharednotes.user.dto.UserUpdateDTO;
import com.eugene.sharednotes.exceptions.UserAlreadyExistsException;
import com.eugene.sharednotes.exceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Сервис управления пользователями: CRUD-операции над {@link UserEntity}
 * Не отвечает за хеширование пароля — ожидается, что вызывающая сторона
 * передаёт уже обработанное значение.
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
    public Page<UserResponseDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponseDTO::from);
    }

    /**
     * Возвращает пользователя c указанным id.
     *
     * @return информацию о пользователе (за исключением пароля).
     * @throws UserNotFoundException если пользователь с данным id не найден.
     */
    public UserResponseDTO findById(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return UserResponseDTO.from(user);

    }

    public UserResponseDTO findByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        return UserResponseDTO.from(user);
    }

    /**
     * Создаёт пользователя.
     * Проверяет существование пользователя по полям email и username.
     *
     * @param createUserDTO валидированные поля для создания пользователя:
     *                      <ul>
     *                          <li>{@code username} имя пользователя,</li>
     *                          <li>{@code password} пароль пользователя (в открытом виде),</li>
     *                          <li>{@code email}    email пользователя,</li>
     *                          <li>{@code role}     роль пользователя.</li>
     *                      </ul>
     * @return информацию о созданном пользователе (за исключением пароля).
     * @throws UserAlreadyExistsException если пользователь с данным email или username уже существует.
     */
    public UserResponseDTO createUser(CreateUserDTO createUserDTO) {
        if (userRepository.existsByEmail(createUserDTO.email()))
            throw new UserAlreadyExistsException("User already exists with this email: " + createUserDTO.email());

        if (userRepository.existsByUsername(createUserDTO.username()))
            throw new UserAlreadyExistsException("User already exists with this username: " + createUserDTO.username());

        UserEntity newUser = UserEntity.builder()
                .username(createUserDTO.username())
                .role(createUserDTO.role())
                .password(passwordEncoder.encode(createUserDTO.password()))
                .email(createUserDTO.email())
                .role(createUserDTO.role() != null ? createUserDTO.role() : Role.USER)
                .build();

        userRepository.save(newUser);

        return UserResponseDTO.from(newUser);
    }

    /**
     * Изменяет поля существующего пользователя.
     * Проверяет какие поля в запросе ненулевые и применяет изменения в случае несовпадения.
     *
     * @param updateUserDTO поля, которые необходимо изменить.
     * @return новую информацию о пользователе (за исключением пароля).
     * @throws UserNotFoundException      если пользователя с данным id не найден.
     * @throws UserAlreadyExistsException если новый username или email уже занят другим пользователем.
     */
    @Transactional
    public UserResponseDTO updateUser(Long id, UserUpdateDTO updateUserDTO) {
        UserEntity updateUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (updateUserDTO.username() != null && !updateUserDTO.username().isBlank()) {
            if (!updateUserDTO.username().equals(updateUser.getUsername())
                    && userRepository.existsByUsername(updateUserDTO.username()))
                throw new UserAlreadyExistsException("User already exists with this username: " + updateUserDTO.username());

            updateUser.setUsername(updateUserDTO.username());
        }

        if (updateUserDTO.password() != null && !updateUserDTO.password().isBlank())
            updateUser.setPassword(passwordEncoder.encode(updateUserDTO.password()));

        if (updateUserDTO.email() != null && !updateUserDTO.email().isBlank()) {
            if (!updateUserDTO.email().equals(updateUser.getEmail())
                    && userRepository.existsByEmail(updateUserDTO.email()))
                throw new UserAlreadyExistsException("User already exists with this email: " + updateUserDTO.email());

            updateUser.setEmail(updateUserDTO.email());
        }

        return UserResponseDTO.from(updateUser);
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
