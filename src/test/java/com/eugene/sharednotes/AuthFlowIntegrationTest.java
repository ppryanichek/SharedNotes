package com.eugene.sharednotes;

import com.eugene.sharednotes.auth.RefreshToken;
import com.eugene.sharednotes.auth.RefreshTokenRepository;
import com.eugene.sharednotes.auth.dto.TokenPairResponse;
import com.eugene.sharednotes.exceptions.dto.ErrorCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private String refreshBody(String token) {
        return """
                {"refreshToken": "%s"}
                """.formatted(token);
    }

    private MockHttpServletRequestBuilder postBody(String uri, String body) {
        return post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private TokenPairResponse register(String username, String email) throws Exception {
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "%s",
                                    "password": "Password1@",
                                    "email": "%s"
                                }
                                """.formatted(username, email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new TokenPairResponse(
                JsonPath.read(response, "$.accessToken"),
                JsonPath.read(response, "$.refreshToken")
        );
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldRegisterLoginAndAccessProtectedEndpoint() throws Exception {
        register("Bob", "bob@mail.ru");

        String response = mockMvc.perform(postBody("/auth/login",
                        """
                                {
                                "email": "bob@mail.ru",
                                "password": "Password1@"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = JsonPath.read(response, "$.accessToken");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Bob"));

    }

    @Test
    void shouldReturn401AndNoRollback_whenReuseDetected() throws Exception {
        String oldToken = register("Bob", "bob@mail.ru").refreshToken();

        mockMvc.perform(postBody("/auth/refresh", refreshBody(oldToken)))
                .andExpect(status().isOk());

        mockMvc.perform(postBody("/auth/refresh", refreshBody(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_REVOKED.toString()));

        assertThat(refreshTokenRepository.findAll())
                .isNotEmpty()
                .allMatch(RefreshToken::isRevoked);
    }

    @Nested
    class ChangePassword {

        private TokenPairResponse oldTokens;
        private String response;

        @BeforeEach
        void setUp() throws Exception {
            oldTokens = register("Bob", "bob@mail.ru");

            response = mockMvc.perform(postBody("/auth/change-password",
                            """
                                    {
                                    "oldPassword": "Password1@",
                                    "newPassword": "newPassword1@"
                                    }
                                    """)
                            .header("Authorization", "Bearer " + oldTokens.accessToken()))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }

        @Test
        void shouldRevokeOldRefreshToken() throws Exception {
            // Old Refresh
            mockMvc.perform(postBody("/auth/refresh", refreshBody(oldTokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldIssueNewTokens() throws Exception {
            TokenPairResponse newTokens = objectMapper.readValue(response, TokenPairResponse.class);

            // New Access
            mockMvc.perform(get("/users/me")
                            .header("Authorization", "Bearer " + newTokens.accessToken()))
                    .andExpect(status().isOk());

            // New Refresh
            mockMvc.perform(postBody("/auth/refresh", refreshBody(newTokens.refreshToken())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void shouldRevokeCurrentSession_whenLogout() throws Exception {
        String oldToken = register("Bob", "bob@mail.ru").refreshToken();

        mockMvc.perform(postBody("/auth/logout", refreshBody(oldToken)))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findAll())
                .isNotEmpty()
                .allMatch(RefreshToken::isRevoked);
    }
}
