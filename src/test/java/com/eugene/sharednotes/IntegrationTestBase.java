package com.eugene.sharednotes;

import com.eugene.sharednotes.auth.dto.RegisterRequest;
import com.eugene.sharednotes.auth.dto.TokenPairResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTestBase {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected TokenPairResponse register(String username, String email) throws Exception {
        ResultActions result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new RegisterRequest(username, "Password1@", email))))
                .andExpect(status().isOk());

        return bodyOf(result, TokenPairResponse.class);
    }

    protected String bodyOf(ResultActions result) throws Exception {
        return result.andReturn()
                .getResponse()
                .getContentAsString();
    }

    protected <T> T bodyOf(ResultActions result, Class<T> type) throws Exception {
        return jsonMapper.readValue(bodyOf(result), type);
    }

    protected RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    @BeforeEach
    protected void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}
