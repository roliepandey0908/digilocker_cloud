
package com.digilocker.digilockerbackend.controller;

import com.digilocker.digilockerbackend.dto.request.AccessRequestCreateRequest;
import com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse;
import com.digilocker.digilockerbackend.dto.response.AccessRequestResponse;
import com.digilocker.digilockerbackend.dto.response.VerificationResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/requester")
public class RequesterController {

    @PostMapping("/requests")
    @PreAuthorize("hasRole('REQUESTER')")
    @Operation(summary = "Create a document access request")
    public ResponseEntity<AccessRequestResponse> createRequest(
            @Valid @RequestBody AccessRequestCreateRequest request
    ) {

        // TODO: Derive requester identity from JWT and create the request.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('REQUESTER')")
    @Operation(summary = "Get requests created by the requester")
    public ResponseEntity<AccessRequestListResponse> getRequests() {

        // TODO: Fetch requests created by the authenticated requester.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/requests/{requestId}")
    @PreAuthorize("hasRole('REQUESTER')")
    @Operation(summary = "Get details of an access request")
    public ResponseEntity<AccessRequestResponse> getRequest(
            @PathVariable String requestId
    ) {

        // TODO: Fetch the request and verify requester ownership.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/requests/{requestId}/verify")
    @PreAuthorize("hasRole('REQUESTER')")
    @Operation(summary = "Verify an approved document")
    public ResponseEntity<VerificationResponse> verifyRequest(
            @PathVariable String requestId
    ) {

        // TODO: Check consent, retrieve document and verify its signature.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
