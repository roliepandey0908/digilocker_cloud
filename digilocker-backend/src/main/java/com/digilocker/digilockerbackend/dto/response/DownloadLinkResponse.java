
package com.digilocker.digilockerbackend.dto.response;

import java.time.Instant;

public class DownloadLinkResponse {

    private String documentId;
    private String downloadUrl;
    private Instant expiresAt;

    public DownloadLinkResponse() {
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
