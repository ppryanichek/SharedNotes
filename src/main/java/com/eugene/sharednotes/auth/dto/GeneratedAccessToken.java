package com.eugene.sharednotes.auth.dto;

import java.time.Instant;

public record GeneratedAccessToken(
        String token,
        Instant expiresAt
) {}
