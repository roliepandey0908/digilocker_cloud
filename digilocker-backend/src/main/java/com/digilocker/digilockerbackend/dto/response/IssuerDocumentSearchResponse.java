
package com.digilocker.digilockerbackend.dto.response;

import java.util.List;

public class IssuerDocumentSearchResponse {

    private List<DocumentResponse> documents;
    private Integer totalCount;

    public IssuerDocumentSearchResponse() {
    }

    public List<DocumentResponse> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentResponse> documents) {
        this.documents = documents;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
}
