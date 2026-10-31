package com.eugene.sharednotes.user;

import com.eugene.sharednotes.ControllerTestBase;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.error.dto.ErrorCode;
import com.eugene.sharednotes.user.dto.UserResponse;
import com.eugene.sharednotes.user.dto.UserUpdateRequest;
import com.eugene.sharednotes.user.exceptions.UserAlreadyExistsException;
import com.eugene.sharednotes.user.exceptions.UserNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTestBase {

    private static final Long USER_ID = 1L;

    private static final RequestPostProcessor AUTH_USER = authAs(USER_ID, Role.USER);

    @MockitoBean
    private UserService userService;

    @Nested
    class GetCurrentUser {
        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/users/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturnUserInfo_whenAuthenticated() throws Exception {
            when(userService.findById(USER_ID))
                    .thenReturn(new UserResponse(USER_ID, "Bob", "bob@mail.ru", Role.USER));

            mockMvc.perform(get("/users/me").with(AUTH_USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("Bob"))
                    .andExpect(jsonPath("$.email").value("bob@mail.ru"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void shouldReturn404WithErrorResponse_whenUserDoesNotExist() throws Exception {
            when(userService.findById(USER_ID))
                    .thenThrow(new UserNotFoundException("User not found with id: " + USER_ID));

            mockMvc.perform(get("/users/me").with(AUTH_USER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    class UpdateCurrentUser {
        private static final UserUpdateRequest REQUEST =
                new UserUpdateRequest("eugene", "eugene@mail.ru");

        private MockHttpServletRequestBuilder updateRequest(String username, String email) {
            return patch("/users/me")
                    .with(AUTH_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new UserUpdateRequest(username, email)));
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(patch("/users/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldUpdateUser_whenAuthenticatedAndValidRequest() throws Exception {
            when(userService.updateUser(USER_ID, REQUEST))
                    .thenReturn(new UserResponse(USER_ID, "eugene", "eugene@mail.ru", Role.USER));

            mockMvc.perform(updateRequest("eugene", "eugene@mail.ru"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("eugene"))
                    .andExpect(jsonPath("$.email").value("eugene@mail.ru"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void shouldReturn409WithErrorResponse_whenUserAlreadyExists() throws Exception {
            when(userService.updateUser(USER_ID, REQUEST))
                    .thenThrow(new UserAlreadyExistsException("err message"));

            mockMvc.perform(updateRequest("eugene", "eugene@mail.ru"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_EXISTS.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn404WithErrorResponse_whenUserDoesNotExist() throws Exception {
            when(userService.updateUser(USER_ID, REQUEST))
                    .thenThrow(new UserNotFoundException("err message"));

            mockMvc.perform(updateRequest("eugene", "eugene@mail.ru"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn400_whenEmailIsInvalid() throws Exception {
            mockMvc.perform(updateRequest(null, "not-an-email"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn400_whenUsernameIsInvalid() throws Exception {
            mockMvc.perform(updateRequest("", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("username"));

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn400_whenNoFieldsProvided() throws Exception {
            mockMvc.perform(updateRequest(null, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].message").value("At least one field must be provided"));

            verifyNoInteractions(userService);
        }
    }

    @Nested
    class DeleteCurrentUser {
        @Test
        void shouldReturn204_whenDeleteUser() throws Exception {
            mockMvc.perform(delete("/users/me").with(AUTH_USER))
                    .andExpect(status().isNoContent());

            verify(userService).deleteUser(USER_ID);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/users/me"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn404WithErrorResponse_whenUserDoesNotExist() throws Exception {
            doThrow(new UserNotFoundException("User not found with id: " + USER_ID))
                    .when(userService).deleteUser(USER_ID);

            mockMvc.perform(delete("/users/me").with(AUTH_USER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }
}