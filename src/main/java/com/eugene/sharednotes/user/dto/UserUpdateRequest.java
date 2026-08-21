package com.eugene.sharednotes.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 2, max = 45)
        String username,

        @Email
        String email
) {
        @AssertTrue(message = "At least one field must be provided")
        public boolean isAtLeastOneFieldPresent() {
                return username !=null || email != null;
        }
}
