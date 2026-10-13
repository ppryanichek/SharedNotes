package com.eugene.sharednotes.security;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Бин для проверки актуальной роли пользователя на админских эндпоинтах.
 * <p>Роль в access-токене остаётся прежней до его истечения,
 * поэтому проходит правило {@code hasRole} даже после изменения.
 */
@Component("roleChecker")
@RequiredArgsConstructor
public class RoleChecker {

    private final UserRepository userRepository;

    public boolean isStillAdmin(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof JwtPrincipal(Long id)))
            return false;

        return userRepository
                .findRoleById(id)
                .map(role -> role == Role.ADMIN).orElse(false);
    }
}
