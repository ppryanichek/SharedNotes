package com.eugene.sharednotes.user;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.security.JwtPrincipal;
import com.eugene.sharednotes.security.RoleChecker;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleCheckerTest {

    private static final Long USER_ID = 1L;

    private static final Authentication AUTH_ADMIN =
            new UsernamePasswordAuthenticationToken(
                    new JwtPrincipal(USER_ID),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );

    private static final Authentication AUTH_ANONYMOUS =
            new AnonymousAuthenticationToken(
                    "key",
                    "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleChecker roleChecker;

    @Nested
    class IsStillAdmin {

        @Test
        void shouldReturnFalse_whenPrincipalIsWrongType() {
            assertThat(roleChecker.isStillAdmin(AUTH_ANONYMOUS)).isFalse();

            verifyNoInteractions(userRepository);
        }

        @Test
        void shouldReturnTrue_whenUserIsAdmin() {
            when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.of(Role.ADMIN));

            assertThat(roleChecker.isStillAdmin(AUTH_ADMIN)).isTrue();
        }

        @Test
        void shouldReturnFalse_whenUserIsNotAdmin() {
            when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.of(Role.USER));

            assertThat(roleChecker.isStillAdmin(AUTH_ADMIN)).isFalse();
        }

        @Test
        void shouldReturnFalse_whenUserDoesNotExist() {
            when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.empty());

            assertThat(roleChecker.isStillAdmin(AUTH_ADMIN)).isFalse();
        }
    }
}