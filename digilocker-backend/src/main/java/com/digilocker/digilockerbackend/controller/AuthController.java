package com.digilocker.digilockerbackend.controller;
import com.digilocker.digilockerbackend.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
import com.digilocker.digilockerbackend.dto.auth.AuthStartRequest;
import com.digilocker.digilockerbackend.dto.auth.AuthStartResponse;
import com.digilocker.digilockerbackend.dto.auth.AuthVerifyRequest;
import com.digilocker.digilockerbackend.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        UserProfileResponse response = new UserProfileResponse();

        response.setUserId(jwt.getSubject());
        response.setUsername(jwt.getClaimAsString("username"));
        response.setRoles(jwt.getClaimAsStringList("cognito:groups"));

        return ResponseEntity.ok(response);
    }
    @SecurityRequirements
    @PostMapping("/start")
    public ResponseEntity<AuthStartResponse> startLogin(
            @Valid @RequestBody AuthStartRequest request) {

        AuthStartResponse response =
                authService.startLogin(request.email());

        return ResponseEntity.ok(response);
    }
    @SecurityRequirements
    @PostMapping("/verify")
    public ResponseEntity<AuthService.AuthVerifyResult> verifyOtp(
            @Valid @RequestBody AuthVerifyRequest request) {

        AuthService.AuthVerifyResult response =
                authService.verifyOtp(
                        request.email(),
                        request.otp(),
                        request.session()
                );

        return ResponseEntity.ok(response);
    }
}