package com.eugene.sharednotes.user;


import com.eugene.sharednotes.auth.jwt.JwtService;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.exceptions.UserAlreadyExistsException;
import com.eugene.sharednotes.exceptions.UserNotFoundException;
import com.eugene.sharednotes.exceptions.dto.ErrorCode;
import com.eugene.sharednotes.security.SecurityConfig;
import com.eugene.sharednotes.security.SecurityUser;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import com.eugene.sharednotes.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private SecurityUser securityUser;

    private SecurityUser adminUser;

    @BeforeEach
    void setUp() {
        UserEntity existingUser = UserEntity.builder()
                .id(USER_ID)
                .username("Bob")
                .email("bob@mail.ru")
                .role(Role.USER)
                .build();

        UserEntity admin = UserEntity.builder()
                .id(2L)
                .username("Admin")
                .email("admin@mail.ru")
                .role(Role.ADMIN)
                .build();

        adminUser = new SecurityUser(admin);
        securityUser = new SecurityUser(existingUser);
    }

    @Nested
    class FindAll {

        private MockHttpServletRequestBuilder findAllRequest() {
            return get("/admin/users")
                    .param("page", "0")
                    .param("size", "20");
        }

        @Test
        void shouldReturnListOfUsers() throws Exception {
            when(userService.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(
                            new UserResponse(1L, "Bob", "bob@mail.ru", Role.USER))));

            mockMvc.perform(findAllRequest().with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].username").value("Bob"))
                    .andExpect(jsonPath("$.content[0].password").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").value(1));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userService).findAll(captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(findAllRequest())
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn403_whenNotAuthorized() throws Exception {
            mockMvc.perform(findAllRequest().with(user(securityUser)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

    }

    @Nested
    class CreateUser {

        private MockHttpServletRequestBuilder createRequest(
                String username, String password, String email) {

            return post("/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                            "username": "%s",
                            "password": "%s",
                            "email": "%s",
                            "role": "%s"
                            }
                            """.formatted(username, password, email, Role.USER));
        }

        @Test
        void shouldReturn201WithUserInformation_whenRequestIsValid() throws Exception {
            when(userService.createUser(new CreateUserRequest("Bob", "Password1@", "bob@mail.ru", Role.USER)))
                    .thenReturn(new UserResponse(1L, "Bob", "bob@mail.ru", Role.USER));

            mockMvc.perform(createRequest(
                            "Bob", "Password1@", "bob@mail.ru")
                            .with(user(adminUser)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/admin/users/1")))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("Bob"))
                    .andExpect(jsonPath("$.email").value("bob@mail.ru"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void shouldReturn400_whenRequestFieldsAreBlank() throws Exception {
            mockMvc.perform(createRequest("", "", "")
                    .with(user(adminUser)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[*].field")
                            .value(hasItems("username", "password", "email")));

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn400_whenPasswordIsInvalid() throws Exception {
            mockMvc.perform(createRequest("Bob", "a", "bob@mail.ru")
                    .with(user(adminUser)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("password"))
                    .andExpect(jsonPath("$.errors[0].message").exists());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn400_whenUsernameIsInvalid() throws Exception {
            mockMvc.perform(createRequest("a", "Password1@", "bob@mail.ru")
                            .with(user(adminUser)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("username"));

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn400_whenEmailIsInvalid() throws Exception {
            mockMvc.perform(createRequest("Bob", "Password1@", "not-an-email")
                            .with(user(adminUser)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(createRequest(
                            "Bob", "Password1@", "bob@mail.ru"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn403_whenNotAuthorized() throws Exception {
            mockMvc.perform(createRequest(
                            "Bob", "Password1@", "bob@mail.ru")
                            .with(user(securityUser)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn409_whenUserAlreadyExists() throws Exception {
            when(userService.createUser(new CreateUserRequest(
                    "Bob", "Password1@", "bob@mail.ru", Role.USER)))
                    .thenThrow(new UserAlreadyExistsException("error message"));

            mockMvc.perform(createRequest("Bob", "Password1@", "bob@mail.ru")
                    .with(user(adminUser)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_EXISTS.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    class FindById {

        private static MockHttpServletRequestBuilder findByIdRequest(Long id) {
            return get("/admin/users/{id}", id);
        }

        @Test
        void shouldReturnUserInformation_whenRequestIsValid() throws Exception {
            when(userService.findById(USER_ID)).thenReturn(new UserResponse(
                    1L, "Bob", "bob@mail.ru", Role.USER));

            mockMvc.perform(findByIdRequest(USER_ID).with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("Bob"))
                    .andExpect(jsonPath("$.email").value("bob@mail.ru"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void shouldReturn400_whenIdIsNotValid() throws Exception {
            mockMvc.perform(get("/admin/users/abc").with(user(adminUser)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(findByIdRequest(USER_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn403_whenNotAuthorized() throws Exception {
            mockMvc.perform(findByIdRequest(USER_ID).with(user(securityUser)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn404WithErrorResponse_whenUserDoesNotExist() throws Exception {
            when(userService.findById(USER_ID)).thenThrow(new UserNotFoundException("error message"));

            mockMvc.perform(findByIdRequest(USER_ID).with(user(adminUser)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    class FindByUsername {

        private static MockHttpServletRequestBuilder findByUsernameRequest(String username) {
            return get("/admin/users/username/{username}", username);
        }

        @Test
        void shouldReturnUserInformation() throws Exception {
            when(userService.findByUsername("Bob")).thenReturn(new UserResponse(
                    1L, "Bob", "bob@mail.ru", Role.USER));

            mockMvc.perform(findByUsernameRequest("Bob").with(user(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("Bob"))
                    .andExpect(jsonPath("$.email").value("bob@mail.ru"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(findByUsernameRequest("Bob"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn403_whenNotAuthorized() throws Exception {
            mockMvc.perform(findByUsernameRequest("Bob").with(user(securityUser)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn404WithErrorResponse_whenUserDoesNotExist() throws Exception {
            when(userService.findByUsername("Bob")).thenThrow(new UserNotFoundException("error message"));

            mockMvc.perform(findByUsernameRequest("Bob").with(user(adminUser)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    class DeleteUser {

        private static MockHttpServletRequestBuilder deleteRequest(Long id) {
            return delete("/admin/users/{id}", id);
        }

        @Test
        void shouldReturn204_whenDeleteUser() throws Exception {
            mockMvc.perform(deleteRequest(USER_ID).with(user(adminUser)))
                    .andExpect(status().isNoContent());

            verify(userService).deleteUser(USER_ID);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(deleteRequest(USER_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn403_whenNotAuthorized() throws Exception {
            mockMvc.perform(deleteRequest(USER_ID).with(user(securityUser)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(userService);
        }

        @Test
        void shouldReturn404_whenUserDoesNotExist() throws Exception {
            doThrow(new UserNotFoundException("error message"))
                    .when(userService).deleteUser(USER_ID);

            mockMvc.perform(deleteRequest(USER_ID).with(user(adminUser)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.toString()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

}