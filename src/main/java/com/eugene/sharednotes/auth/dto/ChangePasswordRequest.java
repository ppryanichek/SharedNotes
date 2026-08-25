package com.eugene.sharednotes.auth.dto;

import com.eugene.sharednotes.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank
        String oldPassword,

        @NotBlank
        @ValidPassword
        String newPassword

) {}
