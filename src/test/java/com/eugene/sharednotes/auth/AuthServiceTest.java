package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.auth.dto.*;
import com.eugene.sharednotes.auth.jwt.JwtService;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.security.SecurityUser;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserService;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import com.eugene.sharednotes.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты {@link AuthService}.
 * <p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 1L;

    // Момент в котором застыли тесты. Значение произвольное — важна фиксированность.
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private UserEntity existingUser;

    @BeforeEach
    void setUp() {
        existingUser = UserEntity.builder().id(USER_ID).build();
    }

    @Nested
    class Register {

        @BeforeEach
        void setUp() {
            UserResponse userResponse = new UserResponse(
                    USER_ID, "Bob", "bob@mail.ru", Role.USER);

            when(userService.createUser(new CreateUserRequest(
                    "Bob", "password", "bob@mail.ru", Role.USER)))
                    .thenReturn(userResponse);
            when(jwtService.generateAccessToken(USER_ID))
                    .thenReturn(new GeneratedAccessToken("ACCESS", FIXED_NOW.plus(Duration.ofMinutes(15))));
            when(refreshTokenService.create(USER_ID)).thenReturn("NEW-REFRESH");
        }

        @Test
        void shouldSetDefaultRole() {
            authService.register(new RegisterRequest(
                    "Bob", "password", "bob@mail.ru"));

            ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
            verify(userService).createUser(captor.capture());

            assertThat(captor.getValue().role()).isEqualTo(Role.USER);
        }

        @Test
        void shouldIssueTokensForNewUser() {
            TokenPairResponse response = authService.register(new RegisterRequest(
                    "Bob", "password", "bob@mail.ru"));

            assertThat(response.accessToken()).isEqualTo("ACCESS");
            assertThat(response.refreshToken()).isEqualTo("NEW-REFRESH");

        }
    }

    @Test
    void login_shouldIssueTokensForAuthenticatedUser() {
        SecurityUser principal = new SecurityUser(existingUser);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateAccessToken(USER_ID))
                .thenReturn(new GeneratedAccessToken("ACCESS", FIXED_NOW.plus(Duration.ofMinutes(15))));
        when(refreshTokenService.create(USER_ID)).thenReturn("NEW-REFRESH");

        TokenPairResponse response = authService.login(new LoginRequest("bob@mail.ru", "password"));

        assertThat(response.accessToken()).isEqualTo("ACCESS");
        assertThat(response.refreshToken()).isEqualTo("NEW-REFRESH");
    }

    @Nested
    class Refresh {

        @BeforeEach
        void setUp() {
            when(jwtService.generateAccessToken(USER_ID))
                    .thenReturn(new GeneratedAccessToken("ACCESS", FIXED_NOW.plus(Duration.ofMinutes(15))));
            when(refreshTokenService.rotate("REFRESH"))
                    .thenReturn(new RotationResult("NEW-REFRESH", USER_ID));
        }

        @Test
        void shouldRotateTokensAndGenerateAccessTokenForRightUser() {
            authService.refresh("REFRESH");

            verify(refreshTokenService).rotate("REFRESH");
            verify(jwtService).generateAccessToken(USER_ID);
        }

        @Test
        void shouldPutTokensInCorrectFields() {
            TokenPairResponse response = authService.refresh("REFRESH");

            assertThat(response.accessToken()).isEqualTo("ACCESS");
            assertThat(response.refreshToken()).isEqualTo("NEW-REFRESH");
        }
    }

    @Nested
    class ChangePassword {
        private ChangePasswordRequest request;

        @BeforeEach
        void setUp() {
            when(jwtService.generateAccessToken(USER_ID))
                    .thenReturn(new GeneratedAccessToken("ACCESS", FIXED_NOW.plus(Duration.ofMinutes(15))));
            when(refreshTokenService.create(USER_ID))
                    .thenReturn("NEW-REFRESH");

            request = new ChangePasswordRequest("old-pass", "new-pass");
        }

        @Test
        void shouldRevokeAllSessions_whenPasswordChanged() {
            authService.changePassword(USER_ID, request);

            verify(refreshTokenService).revokeAll(USER_ID);
        }

        @Test
        void shouldIssueNewTokens_whenPasswordChanged() {
            TokenPairResponse response = authService.changePassword(USER_ID, request);

            assertThat(response.accessToken()).isEqualTo("ACCESS");
            assertThat(response.refreshToken()).isEqualTo("NEW-REFRESH");
        }
    }

    @Test
    void logout_shouldRevokeGivenToken() {
        authService.logout("REFRESH");

        verify(refreshTokenService).revoke("REFRESH");
    }

    @Test
    void logoutAll_shouldRevokeAllTokensOfGivenUser() {
        authService.logoutAll(USER_ID);

        verify(refreshTokenService).revokeAll(USER_ID);
    }
}