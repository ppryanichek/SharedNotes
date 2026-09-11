package com.eugene.sharednotes.auth;

import com.eugene.sharednotes.auth.dto.*;
import com.eugene.sharednotes.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public TokenPairResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout-all")
    public void logoutAll(@AuthenticationPrincipal SecurityUser principal) {
        authService.logoutAll(principal.getUserId());
    }

    @PostMapping("/change-password")
    public TokenPairResponse changePassword(@AuthenticationPrincipal SecurityUser principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(principal.getUserId(), request);
    }

    @PostMapping("/restore")
    public TokenPairResponse restore(@Valid @RequestBody LoginRequest request) {
        return authService.restore(request);
    }
}
