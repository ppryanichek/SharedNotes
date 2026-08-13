package com.eugene.sharednotes.user;

import com.eugene.sharednotes.security.SecurityUser;
import com.eugene.sharednotes.user.dto.UserResponse;
import com.eugene.sharednotes.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal SecurityUser principal) {
        return userService.findById(principal.getUserId());
    }

    @PatchMapping("/me")
    public UserResponse updateCurrentUser(@AuthenticationPrincipal SecurityUser principal,
                                          @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateUser(principal.getUserId(), request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    public void deleteCurrentUser(@AuthenticationPrincipal SecurityUser principal) {
        userService.deleteUser(principal.getUserId());
    }

}
