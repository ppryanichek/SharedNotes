package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.ControllerTestBase;
import com.eugene.sharednotes.auth.dto.ChangePasswordRequest;
import com.eugene.sharednotes.auth.dto.LoginRequest;
import com.eugene.sharednotes.auth.dto.RegisterRequest;
import com.eugene.sharednotes.auth.dto.TokenPairResponse;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.exceptions.ExpiredRefreshTokenException;
import com.eugene.sharednotes.exceptions.InvalidPasswordException;
import com.eugene.sharednotes.exceptions.RestoreWindowExpiredException;
import com.eugene.sharednotes.exceptions.RevokedTokenException;
import com.eugene.sharednotes.exceptions.UserAlreadyExistsException;
import com.eugene.sharednotes.exceptions.dto.ErrorCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTestBase {

    private static final Long USER_ID = 1L;

    private static final RequestPostProcessor AUTH_USER = authAs(USER_ID, Role.USER);

    @MockitoBean
    private AuthService authService;

    @Nested
    class Register {

        private static MockHttpServletRequestBuilder registerRequest(
                String username, String password, String email) {
            return post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                            "username": "%s",
                            "password": "%s",
                            "email": "%s"
                            }
                            """.formatted(username, password, email));
        }

        @Test
        void shouldReturnTokens_whenRequestIsValid() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(new TokenPairResponse("ACCESS", "REFRESH"));

            mockMvc.perform(registerRequest("Bob", "Password1@", "bob@mail.ru"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("ACCESS"))
                    .andExpect(jsonPath("$.refreshToken").value("REFRESH"));
        }

        // Exceptions
        @Test
        void shouldReturn409WithErrorResponse_whenUserAlreadyExists() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new UserAlreadyExistsException("error message"));

            mockMvc.perform(registerRequest("Bob", "Password1@", "bob@mail.ru"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_EXISTS.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        // Validation
        @Test
        void shouldReturn400_whenRequestFieldsAreBlank() throws Exception {
            mockMvc.perform(registerRequest("", "", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[*].field")
                            .value(hasItems("username", "password", "email")));

            verifyNoInteractions(authService);
        }

        @Test
        void shouldReturn400_whenUsernameIsInvalid() throws Exception {
            mockMvc.perform(registerRequest("a", "Password1@", "bob@mail.ru"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("username"));

            verifyNoInteractions(authService);
        }

        @Test
        void shouldReturn400_whenEmailIsInvalid() throws Exception {
            mockMvc.perform(registerRequest("Bob", "Password1@", "not-an-email"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));

            verifyNoInteractions(authService);
        }

        @Test
        void shouldReturn400_whenPasswordIsInvalid() throws Exception {
            mockMvc.perform(registerRequest("Bob", "weak", "bob@mail.ru"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("password"));

            verifyNoInteractions(authService);
        }
    }

    @Nested
    class Login {

        private static MockHttpServletRequestBuilder loginRequest(String email, String password) {
            return post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                            "email": "%s",
                            "password": "%s"
                            }
                            """.formatted(email, password));
        }

        @Test
        void shouldReturnTokens_whenRequestIsValid() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(new TokenPairResponse("ACCESS", "REFRESH"));

            mockMvc.perform(loginRequest("bob@mail.ru", "Password1@"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("ACCESS"))
                    .andExpect(jsonPath("$.refreshToken").value("REFRESH"));
        }

        // Validation
        @Test
        void shouldReturn400_whenRequestIsBlank() throws Exception {
            mockMvc.perform(loginRequest("", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[*].field")
                            .value(hasItems("email", "password")));

            verifyNoInteractions(authService);
        }

        @Test
        void shouldReturn401_whenCredentialsAreInvalid() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("error message"));

            mockMvc.perform(loginRequest("bob@mail.ru", "WrongPassword1@"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

    }

    @Nested
    class Refresh {

        private static final String REFRESH_TOKEN = "REFRESH";

        private static MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
            return post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"refreshToken": "%s"}
                            """.formatted(refreshToken));
        }

        @Test
        void shouldReturnTokens_whenRequestIsValid() throws Exception {
            when(authService.refresh(REFRESH_TOKEN))
                    .thenReturn(new TokenPairResponse("ACCESS", "NEW-REFRESH"));

            mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("ACCESS"))
                    .andExpect(jsonPath("$.refreshToken").value("NEW-REFRESH"));
        }

        //Validation
        @Test
        void shouldReturn400_whenRequestFieldIsBlank() throws Exception {
            mockMvc.perform(refreshRequest(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));

            verifyNoInteractions(authService);
        }

        // Exceptions
        @Test
        void shouldReturn401_whenTokenDoesNotExist() throws Exception {
            when(authService.refresh(REFRESH_TOKEN))
                    .thenThrow(new BadCredentialsException("error message"));

            mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn401_whenTokenIsExpired() throws Exception {
            when(authService.refresh(REFRESH_TOKEN))
                    .thenThrow(new ExpiredRefreshTokenException("error message"));

            mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn401_whenTokenIsRevoked() throws Exception {
            when(authService.refresh(REFRESH_TOKEN))
                    .thenThrow(new RevokedTokenException("error message"));

            mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_REVOKED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    class Logout {

        private static final String REFRESH_TOKEN = "REFRESH";

        private static MockHttpServletRequestBuilder logoutRequest(String refreshToken) {
            return post("/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"refreshToken": "%s"}
                            """.formatted(refreshToken));
        }

        @Test
        void shouldReturn204_whenRequestIsValid() throws Exception {
            mockMvc.perform(logoutRequest(REFRESH_TOKEN))
                    .andExpect(status().isNoContent());

            verify(authService).logout(REFRESH_TOKEN);
        }

        @Test
        void shouldReturn400_whenRequestFieldIsBlank() throws Exception {
            mockMvc.perform(logoutRequest(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));

            verifyNoInteractions(authService);
        }
    }

    @Nested
    class LogoutAll {

        @Test
        void shouldReturn204_whenAuthenticated() throws Exception {
            mockMvc.perform(post("/auth/logout-all").with(AUTH_USER))
                    .andExpect(status().isNoContent());

            verify(authService).logoutAll(USER_ID);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/auth/logout-all"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authService);
        }
    }

    @Nested
    class ChangePassword {

        private static MockHttpServletRequestBuilder changePasswordRequest(String oldPassword, String newPassword) {
            return post("/auth/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                            "oldPassword": "%s",
                            "newPassword": "%s"
                            }
                            """.formatted(oldPassword, newPassword));
        }

        @Test
        void shouldReturnTokens_whenRequestIsValid() throws Exception {
            when(authService.changePassword(USER_ID,
                    new ChangePasswordRequest("OldPass1@", "NewPass1@")))
                    .thenReturn(new TokenPairResponse("ACCESS", "REFRESH"));

            mockMvc.perform(changePasswordRequest("OldPass1@", "NewPass1@").with(AUTH_USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("ACCESS"))
                    .andExpect(jsonPath("$.refreshToken").value("REFRESH"));
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(changePasswordRequest("OldPass1@", "NewPass1@"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authService);
        }

        @Test
        void shouldReturn400_whenOldPassIsWrong() throws Exception {
            when(authService.changePassword(USER_ID, new ChangePasswordRequest("WrongPass1@", "NewPass1@")))
                    .thenThrow(new InvalidPasswordException("error message"));

            mockMvc.perform(changePasswordRequest("WrongPass1@", "NewPass1@").with(AUTH_USER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PASSWORD.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn400_whenRequestFieldsAreBlank() throws Exception {
            mockMvc.perform(changePasswordRequest("", "").with(AUTH_USER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[*].field")
                            .value(hasItems("oldPassword", "newPassword")));

            verifyNoInteractions(authService);
        }

        @Test
        void shouldReturn400_whenNewPassIsInvalid() throws Exception {
            mockMvc.perform(changePasswordRequest("OldPass1@", "newpass").with(AUTH_USER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].message").exists());

            verifyNoInteractions(authService);
        }
    }

    @Nested
    class Restore {

        private static MockHttpServletRequestBuilder restoreRequest(String email, String password) {
            return post("/auth/restore")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                            "email": "%s",
                            "password": "%s"
                            }
                            """.formatted(email, password));
        }

        @Test
        void shouldReturn410_whenRetentionPeriodExpired() throws Exception {

            when(authService.restore(new LoginRequest("bob@mail.ru", "Password1@")))
                    .thenThrow(new RestoreWindowExpiredException("error message"));

            mockMvc.perform(restoreRequest("bob@mail.ru", "Password1@"))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(ErrorCode.RESTORE_WINDOW_EXPIRED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldIssueTokens_whenRestorationSucceeds() throws Exception {
            when(authService.restore(new LoginRequest("bob@mail.ru", "Password1@")))
                    .thenReturn(new TokenPairResponse("ACCESS", "REFRESH"));

            mockMvc.perform(restoreRequest("bob@mail.ru", "Password1@"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("ACCESS"))
                    .andExpect(jsonPath("$.refreshToken").value("REFRESH"));
        }
    }
}