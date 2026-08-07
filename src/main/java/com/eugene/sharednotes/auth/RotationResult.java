package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.common.Role;

public record RotationResult(
        String rawRefreshToken,
        Long userId,
        Role role
) {}
