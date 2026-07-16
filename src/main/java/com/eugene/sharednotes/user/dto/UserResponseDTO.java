package com.eugene.sharednotes.user.dto;

import com.eugene.sharednotes.user.UserEntity;

public record UserResponseDTO(Long id, String username, String email) {
    public static UserResponseDTO from(UserEntity user){
        return new UserResponseDTO(user.getId(), user.getUsername(), user.getEmail());
    }
}
