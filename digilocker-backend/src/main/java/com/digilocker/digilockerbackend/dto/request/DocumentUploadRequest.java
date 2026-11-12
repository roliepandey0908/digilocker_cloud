
package com.digilocker.digilockerbackend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class DocumentUploadRequest {

    @NotBlank(message = "Enrollment ID is required")
    private String enrollmentId;

    @NotBlank(message = "Document type is required")
    @Pattern(
            regexp = "DEGREE|MARKSHEET",
            message = "Document type must be DEGREE or MARKSHEET"
    )
    private String documentType;

    @NotNull(message = "Passing year is required")
    @Min(value = 1950, message = "Passing year must be at least 1950")
    @Max(value = 2100, message = "Invalid passing year")
    private Integer passingYear;

    public DocumentUploadRequest() {
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public Integer getPassingYear() {
        return passingYear;
    }

    public void setPassingYear(Integer passingYear) {
        this.passingYear = passingYear;
    }
}
