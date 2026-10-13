package com.eugene.sharednotes.user;

import com.eugene.sharednotes.security.JwtPrincipal;
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
    public UserResponse getCurrentUser(@AuthenticationPrincipal JwtPrincipal principal) {
        return userService.findById(principal.id());
    }

    @PatchMapping("/me")
    public UserResponse updateCurrentUser(@AuthenticationPrincipal JwtPrincipal principal,
                                          @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateUser(principal.id(), request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    public void deleteCurrentUser(@AuthenticationPrincipal JwtPrincipal principal) {
        userService.deleteUser(principal.id());
    }

}
