
package com.digilocker.digilockerbackend.controller;

import com.digilocker.digilockerbackend.dto.request.ConsentDecisionRequest;
import com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse;
import com.digilocker.digilockerbackend.dto.response.CitizenDocumentListResponse;
import com.digilocker.digilockerbackend.dto.response.CitizenDocumentResponse;
import com.digilocker.digilockerbackend.dto.response.ConsentDecisionResponse;
import com.digilocker.digilockerbackend.dto.response.DownloadLinkResponse;
import com.digilocker.digilockerbackend.dto.response.IssuerDocumentSearchResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/citizen")
public class CitizenController {

    @GetMapping("/issuer-documents")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Search documents in the issuer repository")
    public ResponseEntity<IssuerDocumentSearchResponse> searchIssuerDocuments(
            @RequestParam(required = false) String enrollmentId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) Integer passingYear
    ) {

        // TODO: Search the simulated issuer repository.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/documents/{documentId}/claim")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Claim an issued document")
    public ResponseEntity<CitizenDocumentResponse> claimDocument(
            @PathVariable String documentId
    ) {

        // TODO: Link the canonical document to the authenticated citizen.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get all documents claimed by the citizen")
    public ResponseEntity<CitizenDocumentListResponse> getDocuments() {

        // TODO: Fetch the citizen's linked documents.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Generate a secure document download link")
    public ResponseEntity<DownloadLinkResponse> downloadDocument(
            @PathVariable String documentId
    ) {

        // TODO: Check ownership and generate a short-lived S3 URL.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get access requests received by the citizen")
    public ResponseEntity<AccessRequestListResponse> getRequests() {

        // TODO: Fetch requests addressed to the authenticated citizen.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/requests/{requestId}/decision")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Approve or reject an access request")
    public ResponseEntity<ConsentDecisionResponse> decideRequest(
            @PathVariable String requestId,
            @Valid @RequestBody ConsentDecisionRequest request
    ) {

        // TODO: Check request ownership and save the consent decision.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
