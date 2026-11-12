package com.digilocker.digilockerbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthVerifyRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "OTP is required")
        String otp,

        @NotBlank(message = "Authentication session is required")
        String session

) {}