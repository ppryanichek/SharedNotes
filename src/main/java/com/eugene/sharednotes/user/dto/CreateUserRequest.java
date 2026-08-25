package com.eugene.sharednotes.user.dto;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(min = 2, max = 45)
        String username,

        @NotBlank
        @ValidPassword
        String password,

        @NotBlank
        @Email
        String email,

        Role role
) {}
