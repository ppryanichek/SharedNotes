package com.eugene.sharednotes.user.dto;

import com.eugene.sharednotes.common.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserDTO(
        @NotBlank
        @Size(min = 2, max = 45)
        String username,

        @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}")
        String passwordHash,

        @Email
        String email,

        Role role
) {}
