
package com.digilocker.digilockerbackend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AccessRequestDecisionRequest {

    @NotBlank(message = "Decision is required")
    @Pattern(
            regexp = "APPROVED|REJECTED",
            message = "Decision must be APPROVED or REJECTED"
    )
    private String decision;

    @Min(value = 1, message = "Consent duration must be at least 1 minute")
    private Integer consentDurationMinutes;

    public AccessRequestDecisionRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Integer getConsentDurationMinutes() {
        return consentDurationMinutes;
    }

    public void setConsentDurationMinutes(Integer consentDurationMinutes) {
        this.consentDurationMinutes = consentDurationMinutes;
    }
}
