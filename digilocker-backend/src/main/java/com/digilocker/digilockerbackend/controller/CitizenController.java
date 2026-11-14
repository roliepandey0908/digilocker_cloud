package com.digilocker.digilockerbackend.controller;

import java.util.Map;

import com.digilocker.digilockerbackend.dto.request.ConsentDecisionRequest;
import com.digilocker.digilockerbackend.dto.request.CitizenDocumentSearchRequest;
import com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse;
import com.digilocker.digilockerbackend.dto.response.CitizenDocumentListResponse;
import com.digilocker.digilockerbackend.dto.response.CitizenDocumentResponse;
import com.digilocker.digilockerbackend.dto.response.ConsentDecisionResponse;
import com.digilocker.digilockerbackend.dto.response.DownloadLinkResponse;
import com.digilocker.digilockerbackend.dto.response.IssuerDocumentSearchResponse;
import com.digilocker.digilockerbackend.dto.response.IssuerListResponse;
import com.digilocker.digilockerbackend.service.CitizenService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/citizen")
public class CitizenController {

    private final CitizenService citizenService;

    public CitizenController(CitizenService citizenService) {
        this.citizenService = citizenService;
    }

    @GetMapping("/issuers")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get available issuers")
    public ResponseEntity<IssuerListResponse> getIssuers() {
        return ResponseEntity.ok(citizenService.getIssuers());
    }

    @GetMapping("/issuers/{issuerId}/document-types")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get document types configured by a selected issuer")
    public ResponseEntity<?> getIssuerDocumentTypes(
            @PathVariable String issuerId) {

        return ResponseEntity.ok(
                citizenService.getIssuerDocumentTypes(issuerId)
        );
    }

    @GetMapping("/issuers/{issuerId}/document-types/{documentTypeId}/fields")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Get search fields for an issuer document type")
    public ResponseEntity<Map<String, Object>> getDocumentTypeFields(
            @PathVariable String issuerId,
            @PathVariable String documentTypeId) {

        return ResponseEntity.ok(
                citizenService.getDocumentTypeFields(
                        issuerId,
                        documentTypeId
                )
        );
    }

    @PostMapping("/documents/get")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Find and automatically link an issued document")
    public ResponseEntity<IssuerDocumentSearchResponse> getDocument(
            @Valid @RequestBody CitizenDocumentSearchRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                citizenService.getDocument(request, jwt.getSubject())
        );
    }

    @GetMapping("/documents")
    public ResponseEntity<IssuerDocumentSearchResponse> getDocuments(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                citizenService.getDocuments(jwt.getSubject())
        );
    }


    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Map<String, String>> downloadDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {

        String downloadUrl = citizenService.downloadDocument(
                documentId,
                jwt.getSubject()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Download link generated successfully.",
                        "downloadUrl", downloadUrl,
                        "expiresInSeconds", "300"
                )
        );
    }





}
