
package com.digilocker.digilockerbackend.dto.response;

import java.util.List;

public class IssuerDocumentSearchResponse {

    private List<SearchDocument> documents;
    private Integer totalCount;
    private String message;

    public List<SearchDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<SearchDocument> documents) {
        this.documents = documents;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class SearchDocument {

        private String documentId;
        private String documentType;
        private String status;
        private String viewUrl;
        private Integer urlExpiresInSeconds;

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getDocumentType() {
            return documentType;
        }

        public void setDocumentType(String documentType) {
            this.documentType = documentType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getViewUrl() {
            return viewUrl;
        }

        public void setViewUrl(String viewUrl) {
            this.viewUrl = viewUrl;
        }

        public Integer getUrlExpiresInSeconds() {
            return urlExpiresInSeconds;
        }

        public void setUrlExpiresInSeconds(Integer urlExpiresInSeconds) {
            this.urlExpiresInSeconds = urlExpiresInSeconds;
        }
    }
}
