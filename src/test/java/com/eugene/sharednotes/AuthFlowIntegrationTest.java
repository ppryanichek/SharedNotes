package com.eugene.sharednotes;

import com.eugene.sharednotes.auth.RefreshToken;
import com.eugene.sharednotes.auth.RefreshTokenRepository;
import com.eugene.sharednotes.auth.dto.ChangePasswordRequest;
import com.eugene.sharednotes.auth.dto.LoginRequest;
import com.eugene.sharednotes.auth.dto.RefreshTokenRequest;
import com.eugene.sharednotes.auth.dto.TokenPairResponse;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.exceptions.dto.ErrorCode;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    private MockHttpServletRequestBuilder postBody(String uri, Object request) {
        return post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request));
    }

    @Test
    void shouldRegisterLoginAndAccessProtectedEndpoint() throws Exception {
        register("Bob", "bob@mail.ru");

        ResultActions loginResult = mockMvc
                .perform(postBody(
                        "/auth/login",
                        new LoginRequest("bob@mail.ru", "Password1@")))
                .andExpect(status().isOk());

        String accessToken = bodyOf(loginResult, TokenPairResponse.class).accessToken();

        mockMvc
                .perform(get("/users/me")
                        .with(bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Bob"));
    }

    @Test
    void shouldReturn401AndNoRollback_whenReuseDetected() throws Exception {
        String oldToken = register("Bob", "bob@mail.ru").refreshToken();

        mockMvc.perform(postBody("/auth/refresh", new RefreshTokenRequest(oldToken)))
                .andExpect(status().isOk());

        mockMvc.perform(postBody("/auth/refresh", new RefreshTokenRequest(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_REVOKED.name()));

        assertThat(refreshTokenRepository.findAll())
                .isNotEmpty()
                .allMatch(RefreshToken::isRevoked);
    }

    @Nested
    class ChangePassword {

        private TokenPairResponse oldTokens;
        private TokenPairResponse newTokens;

        @BeforeEach
        void setUp() throws Exception {
            oldTokens = register("Bob", "bob@mail.ru");

            ResultActions result = mockMvc
                    .perform(postBody("/auth/change-password",
                            new ChangePasswordRequest("Password1@", "newPassword1@"))
                            .with(bearer(oldTokens.accessToken())))
                    .andExpect(status().isOk());

            newTokens = bodyOf(result, TokenPairResponse.class);
        }

        @Test
        void shouldRevokeOldRefreshToken() throws Exception {
            // Old Refresh
            mockMvc.perform(postBody("/auth/refresh", new RefreshTokenRequest(oldTokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldIssueNewTokens() throws Exception {
            // New Access
            mockMvc.perform(get("/users/me")
                            .with(bearer(newTokens.accessToken())))
                    .andExpect(status().isOk());

            // New Refresh
            mockMvc.perform(postBody("/auth/refresh", new RefreshTokenRequest(newTokens.refreshToken())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void shouldRevokeCurrentSession_whenLogout() throws Exception {
        String oldToken = register("Bob", "bob@mail.ru").refreshToken();

        mockMvc.perform(postBody("/auth/logout", new RefreshTokenRequest(oldToken)))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findAll())
                .isNotEmpty()
                .allMatch(RefreshToken::isRevoked);
    }

    @Test
    void shouldReturn403_whenNoLongerAdmin() throws Exception {
        UserEntity testAdmin = UserEntity.builder()
                .username("TestAdmin")
                .email("admin@mail.ru")
                .password(passwordEncoder.encode("Password1@"))
                .role(Role.ADMIN)
                .build();

        Long adminId = userRepository.save(testAdmin).getId();

        ResultActions result = mockMvc.perform(postBody("/auth/login",
                        new LoginRequest("admin@mail.ru", "Password1@")))
                .andExpect(status().isOk());

        String accessToken = bodyOf(result, TokenPairResponse.class).accessToken();

        mockMvc.perform(get("/admin/users")
                        .with(bearer(accessToken)))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", Role.USER.name(), adminId);

        mockMvc.perform(get("/admin/users")
                        .with(bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.name()));
    }
}