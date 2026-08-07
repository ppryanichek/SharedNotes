package com.eugene.sharednotes.auth.dto;

public record TokenPairResponse(
        String accessToken,
        String refreshToken
) {}
