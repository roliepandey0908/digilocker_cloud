package com.digilocker.digilockerbackend.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record DocumentUploadRequest(

        @NotBlank(message = "Document type ID is required")
        @Size(max = 100)
        String documentTypeId,

        @NotEmpty(message = "Document metadata is required")
        Map<String, Object> metadata

) {
}