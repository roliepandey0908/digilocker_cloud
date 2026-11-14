
package com.digilocker.digilockerbackend.controller;

import com.digilocker.digilockerbackend.dto.request.AccessRequestCreateRequest;
import com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse;
import com.digilocker.digilockerbackend.dto.response.AccessRequestResponse;
import com.digilocker.digilockerbackend.dto.response.IssuerListResponse;
import com.digilocker.digilockerbackend.dto.response.VerificationResponse;
import com.digilocker.digilockerbackend.service.CitizenService;

import com.digilocker.digilockerbackend.service.RequesterService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requester")
@PreAuthorize("hasRole('REQUESTER')")
public class RequesterController {

    private final CitizenService citizenService;
    private final RequesterService requesterService;

    public RequesterController(RequesterService requesterService,
                               CitizenService citizenService) {
        this.requesterService = requesterService;
        this.citizenService = citizenService;
    }


    @GetMapping("/issuers")
    @Operation(summary = "Get available issuers")
    public ResponseEntity<IssuerListResponse> getIssuers() {
        return ResponseEntity.ok(citizenService.getIssuers());
    }

    @GetMapping("/issuers/{issuerId}/document-types")
    @Operation(summary = "Get document types offered by an issuer")
    public ResponseEntity<List<Map<String, Object>>> getIssuerDocumentTypes(
            @PathVariable String issuerId) {

        return ResponseEntity.ok(
                citizenService.getIssuerDocumentTypes(issuerId)
        );
    }

    @PostMapping("/requests")
    @Operation(summary = "Create a document access request")
    public ResponseEntity<AccessRequestResponse> createRequest(
            @Valid @RequestBody AccessRequestCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AccessRequestResponse response = requesterService.createRequest(
                request,
                jwt.getSubject()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests")
    @Operation(summary = "Get requests created by the requester")
    public ResponseEntity<AccessRequestListResponse> getRequests(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "Get details of an access request")
    public ResponseEntity<AccessRequestResponse> getRequest(
            @PathVariable String requestId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/requests/{requestId}/verify")
    @Operation(summary = "Verify an approved document")
    public ResponseEntity<VerificationResponse> verifyRequest(
            @PathVariable String requestId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
