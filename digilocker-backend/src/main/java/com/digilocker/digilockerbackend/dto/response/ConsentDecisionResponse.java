package com.digilocker.digilockerbackend.dto.response;

import java.time.Instant;

public class ConsentDecisionResponse {

    private String requestId;
    private String decision;
    private String consentStatus;
    private Instant approvalCreatedAt;
    private Integer consentDurationMinutes;
    private Instant expiresAt;

    public ConsentDecisionResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getConsentStatus() {
        return consentStatus;
    }

    public void setConsentStatus(String consentStatus) {
        this.consentStatus = consentStatus;
    }

    public Instant getApprovalCreatedAt() {
        return approvalCreatedAt;
    }

    public void setApprovalCreatedAt(Instant approvalCreatedAt) {
        this.approvalCreatedAt = approvalCreatedAt;
    }

    public Integer getConsentDurationMinutes() {
        return consentDurationMinutes;
    }

    public void setConsentDurationMinutes(Integer consentDurationMinutes) {
        this.consentDurationMinutes = consentDurationMinutes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}