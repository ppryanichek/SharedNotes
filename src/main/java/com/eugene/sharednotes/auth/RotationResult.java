package com.eugene.sharednotes.auth;

public record RotationResult(
        String rawRefreshToken,
        Long userId
) {}
