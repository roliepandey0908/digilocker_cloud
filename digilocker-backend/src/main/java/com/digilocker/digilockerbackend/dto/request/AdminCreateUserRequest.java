
package com.digilocker.digilockerbackend.dto.request;

import com.digilocker.digilockerbackend.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotNull
        Role role,

        // Required only for ISSUER
        String issuerName,

        String issuerId,

        String uriPrefix,

        // Required only for REQUESTER
        String organizationName

) {
}
