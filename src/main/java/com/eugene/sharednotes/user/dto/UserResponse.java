package com.eugene.sharednotes.user.dto;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.UserEntity;

public record UserResponse(Long id, String username, String email, Role role) {
    public static UserResponse from(UserEntity user){
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
