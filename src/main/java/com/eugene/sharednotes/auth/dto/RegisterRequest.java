package com.eugene.sharednotes.auth.dto;

import com.eugene.sharednotes.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 2, max = 45)
        String username,

        @NotBlank
        @ValidPassword
        String password,

        @NotBlank
        @Email
        String email
) {}
