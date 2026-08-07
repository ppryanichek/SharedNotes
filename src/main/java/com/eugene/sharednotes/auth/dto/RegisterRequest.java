package com.eugene.sharednotes.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank
        @Size(min = 2, max = 45)
        String username,

        @NotBlank
        @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}")
        String password,

        @NotBlank
        @Email
        String email
) {}
