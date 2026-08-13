package com.eugene.sharednotes.user;

import com.eugene.sharednotes.security.SecurityUser;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import com.eugene.sharednotes.user.dto.UserResponse;
import com.eugene.sharednotes.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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

    @GetMapping
    public Page<UserResponse> findAll(Pageable pageable) {
        return userService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping("/username/{username}")
    public UserResponse findByUsername(@PathVariable String username) { return userService.findByUsername(username); }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
