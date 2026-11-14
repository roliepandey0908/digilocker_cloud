package com.digilocker.digilockerbackend.dto.response;

import java.util.List;

public record IssuerListResponse(List<IssuerSummary> issuers) {

    public record IssuerSummary(
            String issuerId,
            String fullName
    ) {
    }
}