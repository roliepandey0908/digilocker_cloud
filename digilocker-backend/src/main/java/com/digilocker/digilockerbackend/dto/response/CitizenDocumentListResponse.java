
package com.digilocker.digilockerbackend.dto.response;

import java.util.List;

public class CitizenDocumentListResponse {

    private List<CitizenDocumentResponse> documents;
    private Integer totalCount;

    public CitizenDocumentListResponse() {
    }

    public List<CitizenDocumentResponse> getDocuments() {
        return documents;
    }

    public void setDocuments(List<CitizenDocumentResponse> documents) {
        this.documents = documents;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public static class AccessRequestResponse {
    }
}
