
package com.digilocker.digilockerbackend.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record IssuerDocumentTypeRequest(

        @NotBlank
        @Size(max = 100)
        String documentTypeName,

        @NotEmpty
        @Valid
        List<SearchField> searchFields

) {

    public record SearchField(

            @NotBlank
            String fieldKey,

            @NotBlank
            String displayName,

            @NotBlank
            String dataType,

            boolean required

    ) {
    }
}
