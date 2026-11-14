
package com.digilocker.digilockerbackend.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CitizenDocumentSearchRequest(

        @NotBlank
        String issuerId,

        @NotBlank
        String documentTypeId,

        @NotNull
        @NotEmpty
        Map<String, Object> searchCriteria

) {
}
