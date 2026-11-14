
package com.digilocker.digilockerbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateIssuerRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 100)
        String fullName,


        @NotBlank
        String uriPrefix

) {
}
