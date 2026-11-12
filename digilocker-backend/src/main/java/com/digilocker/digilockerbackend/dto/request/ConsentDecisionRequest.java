
package com.digilocker.digilockerbackend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public class ConsentDecisionRequest {

    @Pattern(
            regexp = "APPROVE|REJECT",
            message = "Decision must be APPROVE or REJECT"
    )
    private String decision;

    @Min(
            value = 1,
            message = "Consent duration must be at least 1 day"
    )
    private Integer consentDurationDays;

    public ConsentDecisionRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Integer getConsentDurationDays() {
        return consentDurationDays;
    }

    public void setConsentDurationDays(Integer consentDurationDays) {
        this.consentDurationDays = consentDurationDays;
    }
}
