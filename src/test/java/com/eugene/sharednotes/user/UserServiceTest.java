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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final Duration RETENTION_PERIOD = Duration.ofDays(30);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private UserService userService;

    private UserEntity existingUser;

    @BeforeEach
    void setUp() {

        userService = new UserService(userRepository, passwordEncoder, refreshTokenService, RETENTION_PERIOD, FIXED_CLOCK);

        existingUser = UserEntity.builder()
                .id(1L)
                .username("eugene")
                .email("eugene@mail.ru")
                .password("hash")
                .build();
    }

    @Nested
    class FindById {

        @Test
        void shouldReturnUser_whenUserExists() {
            Long userId = 1L;

            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

            UserResponse response = userService.findById(userId);

            assertThat(response.id()).isEqualTo(userId);
            assertThat(response.username()).isEqualTo("eugene");
            assertThat(response.email()).isEqualTo("eugene@mail.ru");
        }

        @Test
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findById(999L))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class FindByUsername {

        @Test
        void shouldReturnUser_whenUserExists() {
            when(userRepository.findByUsername("eugene")).thenReturn(Optional.of(existingUser));

            UserResponse response = userService.findByUsername("eugene");

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.username()).isEqualTo("eugene");
            assertThat(response.email()).isEqualTo("eugene@mail.ru");
        }

        @Test
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {
            when(userRepository.findByUsername("user")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByUsername("user"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class CreateUser {

        private static final CreateUserRequest createRequestBase = new CreateUserRequest(
                "Bob",
                "Password1@",
                "bob@mail.ru",
                Role.USER
        );

        @Test
        void shouldThrowUserAlreadyExistsException_whenUsernameIsTaken() {
            when(userRepository.existsByUsernameIncludingSoftDeleted("Bob")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(createRequestBase))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        void shouldThrowUserAlreadyExistsException_whenEmailIsTaken() {
            when(userRepository.existsByEmailIncludingSoftDeleted("bob@mail.ru")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(createRequestBase))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        void shouldCreateUser_whenDataIsValid() {
            when(passwordEncoder.encode("Password1@")).thenReturn("hashed-password");

            UserResponse response = userService.createUser(createRequestBase);

            assertThat(response.username()).isEqualTo("Bob");
            assertThat(response.email()).isEqualTo("bob@mail.ru");
            assertThat(response.role()).isEqualTo(Role.USER);
        }

        @Test
        void shouldHashPasswordBeforeSaving() {
            when(passwordEncoder.encode("Password1@")).thenReturn("hashed-password");

            userService.createUser(createRequestBase);

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());

            assertThat(captor.getValue().getPassword()).isEqualTo("hashed-password");
        }

        @Test
        void shouldSetDefaultRole_whenRoleFieldIsNull() {
            CreateUserRequest request = new CreateUserRequest(
                    "Bob",
                    "Password1@",
                    "bob@mail.ru",
                    null
            );

            UserResponse response = userService.createUser(request);

            assertThat(response.role()).isEqualTo(Role.USER);
        }

        @Test
        void shouldSetRole_whenRoleFieldIsNotNull() {
            CreateUserRequest request = new CreateUserRequest(
                    "Bob",
                    "Password1@",
                    "bob@mail.ru",
                    Role.ADMIN
            );

            UserResponse response = userService.createUser(request);

            assertThat(response.role()).isEqualTo(Role.ADMIN);
        }
    }

    @Nested
    class UpdateUser {

        private static UserUpdateRequest updateRequest(String username, String email) {
            return new UserUpdateRequest(username, email);
        }

        // Username
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void shouldNotChangeUsername_whenUsernameIsNullOrBlank(String username) {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            userService.updateUser(1L, updateRequest(username, null));

            assertThat(existingUser.getUsername()).isEqualTo("eugene");
        }

        @Test
        void shouldUpdateUsername_whenUsernameIsFree() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            userService.updateUser(1L, updateRequest("newUsername", null));

            assertThat(existingUser.getUsername()).isEqualTo("newUsername");
        }

        @Test
        void shouldThrowUserAlreadyExistsException_whenNewUsernameIsTaken() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByUsernameIncludingSoftDeleted("newUsername")).thenReturn(true);

            assertThatThrownBy(() ->
                    userService.updateUser(1L, updateRequest("newUsername", null)))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        void shouldNotCheckExistence_whenUsernameIsUnchanged() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            userService.updateUser(1L, updateRequest("eugene", null));

            verify(userRepository, never()).existsByUsernameIncludingSoftDeleted(any());
            assertThat(existingUser.getUsername()).isEqualTo("eugene");
        }

        // Email
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void shouldNotChangeEmail_whenEmailIsNullOrBlank(String email) {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            userService.updateUser(1L, updateRequest(null, email));

            assertThat(existingUser.getEmail()).isEqualTo("eugene@mail.ru");
        }

        @Test
        void shouldUpdateEmail_whenEmailIsFree() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            userService.updateUser(1L, updateRequest(null, "newEmail@mail.ru"));

            assertThat(existingUser.getEmail()).isEqualTo("newEmail@mail.ru");
        }

        @Test
        void shouldThrowUserAlreadyExistsException_whenNewEmailIsTaken() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByEmailIncludingSoftDeleted("newEmail@mail.ru")).thenReturn(true);

            assertThatThrownBy(() ->
                    userService.updateUser(1L, updateRequest(null, "newEmail@mail.ru")))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        void shouldNotCheckExistence_whenEmailIsUnchanged() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            userService.updateUser(1L, updateRequest(null, "eugene@mail.ru"));

            verify(userRepository, never()).existsByEmailIncludingSoftDeleted(any());
            assertThat(existingUser.getEmail()).isEqualTo("eugene@mail.ru");
        }

        @Test
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.updateUser(1L, updateRequest(null, null)))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(1L, "", ""))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void shouldThrowInvalidPasswordException_whenOldPasswordInvalid() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrongPass", "hash")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, "wrongPass", "newPass"))
                    .isInstanceOf(InvalidPasswordException.class);

            assertThat(existingUser.getPassword()).isEqualTo("hash");
        }

        @Test
        void shouldChangePassword_whenDataIsValid() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("1234Qwer@", "hash")).thenReturn(true);
            when(passwordEncoder.encode("newPass")).thenReturn("newPass-hash");

            userService.changePassword(1L, "1234Qwer@", "newPass");

            assertThat(existingUser.getPassword()).isEqualTo("newPass-hash");
        }
    }

    @Nested
    class DeleteUser {

        @Test
        void shouldThrowUserNotFoundExceptionAndNotRevoke_whenUserDoesNotExist() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(1L))
                    .isInstanceOf(UserNotFoundException.class);

            verify(refreshTokenService, never()).revokeAll(any());
        }

        @Test
        void shouldSoftDeleteUserAndRevokeSessions() {
            Long userId = 1L;

            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

            userService.deleteUser(userId);

            assertThat(existingUser.getDeletedAt()).isEqualTo(FIXED_NOW);
            verify(refreshTokenService).revokeAll(userId);
        }

    }

    @Nested
    class Restore {

        @Test
        void shouldThrowUserNotFoundException_whenSoftDeletedUserDoesNotExist() {
           when(userRepository.findSoftDeletedByEmail("eugene@mail.ru")).thenReturn(Optional.empty());

           assertThatThrownBy(() -> userService.restore("eugene@mail.ru", "Password1@"))
                   .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void shouldThrowBadCredentialsException_whenPasswordIsInvalid() {
            when(userRepository.findSoftDeletedByEmail("eugene@mail.ru")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrongPass", "hash")).thenReturn(false);

            assertThatThrownBy(() -> userService.restore("eugene@mail.ru", "wrongPass"))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        void shouldThrowRestorationWindowExpiredException_whenRestorationWindowExpired() {
            existingUser.setDeletedAt(FIXED_NOW.minus(Duration.ofDays(31)));

            when(userRepository.findSoftDeletedByEmail("eugene@mail.ru")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

            assertThatThrownBy(() -> userService.restore("eugene@mail.ru", "pass"))
                    .isInstanceOf(RestoreWindowExpiredException.class);
        }

        @Test
        void shouldRestore_whenDeletedAtExactlyAtRetentionBoundary() {
            existingUser.setDeletedAt(FIXED_NOW.minus(RETENTION_PERIOD));

            when(userRepository.findSoftDeletedByEmail("eugene@mail.ru")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

            userService.restore("eugene@mail.ru", "pass");

            assertThat(existingUser.getDeletedAt()).isNull();
        }

        @Test
        void shouldRestoreAndReturnUser_whenWithinRestorationWindow() {
            UserEntity deletedAdmin = UserEntity.builder()
                    .id(1L)
                    .username("eugene")
                    .email("eugene@mail.ru")
                    .password("hash")
                    .deletedAt(FIXED_NOW.minus(Duration.ofDays(29)))
                    .role(Role.ADMIN)
                    .build();

            when(userRepository.findSoftDeletedByEmail("eugene@mail.ru")).thenReturn(Optional.of(deletedAdmin));
            when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

            UserResponse response = userService.restore("eugene@mail.ru", "pass");

            assertThat(deletedAdmin.getDeletedAt()).isNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.role()).isEqualTo(Role.ADMIN);
        }
    }

    @Test
    void findAll_shouldReturnPageOfUsersResponse() {
        Page<UserEntity> page = new PageImpl<>(List.of(existingUser));

        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<UserResponse> result = userService.findAll(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().username()).isEqualTo(existingUser.getUsername());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void adminExists_shouldReturnRepositoryResult(boolean exists) {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(exists);

        assertThat(userService.adminExists()).isEqualTo(exists);
    }
}