package com.digilocker.digilockerbackend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthStartRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email

) {}