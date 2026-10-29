package com.ridesync.server.controller;

import com.ridesync.server.dto.AvailabilityResponse;
import com.ridesync.server.dto.SignupRequest;
import com.ridesync.server.dto.SignupResponse;
import com.ridesync.server.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @GetMapping("/check-login-id")
    public AvailabilityResponse checkLoginId(
            @RequestParam("loginId") @NotBlank @Size(max = 50) String loginId) {
        return authService.checkLoginId(loginId);
    }

    @GetMapping("/check-nickname")
    public AvailabilityResponse checkNickname(
            @RequestParam("nickname") @NotBlank @Size(max = 50) String nickname) {
        return authService.checkNickname(nickname);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }
}
