package com.digilocker.digilockerbackend.dto.response;

import com.digilocker.digilockerbackend.dto.response.AccessRequestResponse;

import java.util.List;

public class AccessRequestListResponse {

    private List<AccessRequestResponse> requests;
    private Integer totalCount;

    public AccessRequestListResponse() {
    }

    public List<AccessRequestResponse> getRequests() {
        return requests;
    }

    public void setRequests(List<AccessRequestResponse> requests) {
        this.requests = requests;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
}