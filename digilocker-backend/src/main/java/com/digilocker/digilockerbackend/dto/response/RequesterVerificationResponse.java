package com.digilocker.digilockerbackend.dto.response;

import java.time.Instant;

public class RequesterVerificationResponse {

    private String requestId;
    private String status;
    private String documentId;
    private String message;
    private String viewUrl;

    private Instant consentExpiresAt;
    private Instant viewUrlExpiresAt;

    private Long remainingConsentSeconds;

    private Boolean hashValid;
    private Boolean signatureValid;
    private Boolean documentAuthentic;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getViewUrl() {
        return viewUrl;
    }

    public void setViewUrl(String viewUrl) {
        this.viewUrl = viewUrl;
    }

    public Instant getConsentExpiresAt() {
        return consentExpiresAt;
    }

    public void setConsentExpiresAt(Instant consentExpiresAt) {
        this.consentExpiresAt = consentExpiresAt;
    }

    public Instant getViewUrlExpiresAt() {
        return viewUrlExpiresAt;
    }

    public void setViewUrlExpiresAt(Instant viewUrlExpiresAt) {
        this.viewUrlExpiresAt = viewUrlExpiresAt;
    }

    public Long getRemainingConsentSeconds() {
        return remainingConsentSeconds;
    }

    public void setRemainingConsentSeconds(Long remainingConsentSeconds) {
        this.remainingConsentSeconds = remainingConsentSeconds;
    }

    public Boolean getHashValid() {
        return hashValid;
    }

    public void setHashValid(Boolean hashValid) {
        this.hashValid = hashValid;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public Boolean getDocumentAuthentic() {
        return documentAuthentic;
    }

    public void setDocumentAuthentic(Boolean documentAuthentic) {
        this.documentAuthentic = documentAuthentic;
    }
}