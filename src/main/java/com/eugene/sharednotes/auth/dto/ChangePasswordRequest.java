package com.eugene.sharednotes.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(

        @NotBlank
        String oldPassword,

        @NotBlank
        @Pattern(
                regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}",
                message = "Password must me at least 8 characters and include lowercase, uppercase, and digit"
        )
        String newPassword

) {}
