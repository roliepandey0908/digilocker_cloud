
package com.digilocker.digilockerbackend.controller;

import com.digilocker.digilockerbackend.dto.response.DocumentListResponse;
import com.digilocker.digilockerbackend.dto.response.DocumentResponse;
import com.digilocker.digilockerbackend.service.DocumentIdGenerator;
import com.digilocker.digilockerbackend.service.DocumentRegistrationService;
import com.digilocker.digilockerbackend.service.IssuerIdentityService;
import com.digilocker.digilockerbackend.service.S3DocumentService;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/api/issuer")
@Validated
public class IssuerController {

    private final DocumentRegistrationService documentRegistrationService;
    private final S3DocumentService s3DocumentService;
    private final DocumentIdGenerator documentIdGenerator;
    private final IssuerIdentityService issuerIdentityService;

    public IssuerController(
            S3DocumentService s3DocumentService,
            DocumentIdGenerator documentIdGenerator,
            IssuerIdentityService issuerIdentityService,
            DocumentRegistrationService documentRegistrationService
    ) {
        this.s3DocumentService = s3DocumentService;
        this.documentIdGenerator = documentIdGenerator;
        this.issuerIdentityService = issuerIdentityService;
        this.documentRegistrationService = documentRegistrationService;
    }

    @PostMapping(
            value = "/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ISSUER')")
    @Operation(summary = "Upload and register a new document")
    public ResponseEntity<DocumentResponse> uploadDocument(

            @RequestParam("enrollmentId")
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$")
            String enrollmentId,

            @RequestParam("documentType")
            @NotBlank
            @Pattern(regexp = "^(DEGREE|MARKSHEET)$")
            String documentType,

            @RequestParam("passingYear")
            @NotNull
            @Min(1950)
            @Max(2100)
            Integer passingYear,

            @RequestPart("file")
            MultipartFile file,

            @AuthenticationPrincipal Jwt jwt

    ) throws IOException {

        // 1. Get authenticated Cognito user ID
        String cognitoSub = jwt.getSubject();

        // 2. Resolve issuer profile from DynamoDB
        IssuerIdentityService.IssuerProfile issuerProfile =
                issuerIdentityService.getIssuerProfile(cognitoSub);

        String issuerId = issuerProfile.issuerId();

        // 3. Generate document ID
        String documentId =
                documentIdGenerator.generateDocumentId();

        // 4. Generate stable URI
        String documentUri = issuerProfile.uriPrefix()
                + "/"
                + documentType.toLowerCase(Locale.ROOT)
                + "/"
                + documentId;

        // 5. Record upload timestamp
        Instant uploadedAt = Instant.now();

        // 6. Register three DynamoDB records as PENDING_UPLOAD
        String expectedS3Key =
                "incoming/" + issuerId + "/" + documentType + "/" + documentId + ".pdf";

        documentRegistrationService.registerDocument(
                documentId,
                issuerId,
                enrollmentId,
                documentType,
                passingYear,
                expectedS3Key,
                documentUri,
                uploadedAt
        );

        // 7. Upload PDF to S3
        S3DocumentService.UploadedDocument uploadedDocument =
                s3DocumentService.uploadIncomingDocument(
                        file,
                        issuerId,
                        documentType,
                        documentId
                );

        // 8. Mark document as UPLOADED
        documentRegistrationService.markDocumentUploaded(
                documentId,
                issuerId,
                enrollmentId,
                documentType,
                passingYear,
                uploadedDocument.versionId()
        );

        // 9. Prepare response
        DocumentResponse response = new DocumentResponse();

        response.setDocumentId(documentId);
        response.setEnrollmentId(enrollmentId);
        response.setDocumentType(documentType);
        response.setPassingYear(passingYear);
        response.setIssuerId(issuerId);
        response.setStatus("UPLOADED");
        response.setDocumentUri(documentUri);

        // Document is uploaded but not yet signed or issued.
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    private DocumentResponse toDocumentResponse(
            Map<String, AttributeValue> item
    ) {
        DocumentResponse response = new DocumentResponse();

        response.setDocumentId(getString(item, "documentId"));
        response.setEnrollmentId(getString(item, "enrollmentId"));
        response.setDocumentType(getString(item, "documentType"));
        response.setIssuerId(getString(item, "issuerId"));
        response.setStatus(getString(item, "status"));
        response.setDocumentUri(getString(item, "documentUri"));

        String passingYear = getString(item, "passingYear");

        if (passingYear != null) {
            response.setPassingYear(Integer.valueOf(passingYear));
        }

        String issuedAt = getString(item, "issuedAt");

        if (issuedAt != null) {
            response.setIssuedAt(Instant.parse(issuedAt));
        }

        return response;
    }

    private String getString(
            Map<String, AttributeValue> item,
            String attributeName
    ) {
        AttributeValue value = item.get(attributeName);

        return value == null ? null : value.s();
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('ISSUER')")
    @Operation(summary = "Get all documents issued by the issuer")
    public ResponseEntity<DocumentListResponse> getDocuments(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String cognitoSub = jwt.getSubject();

        IssuerIdentityService.IssuerProfile issuerProfile =
                issuerIdentityService.getIssuerProfile(cognitoSub);

        String issuerId = issuerProfile.issuerId();

        List<Map<String, AttributeValue>> items =
                documentRegistrationService.getIssuedDocumentsByIssuer(issuerId);

        List<DocumentResponse> documents = new ArrayList<>();

        for (Map<String, AttributeValue> item : items) {
            documents.add(toDocumentResponse(item));
        }

        DocumentListResponse response = new DocumentListResponse();
        response.setDocuments(documents);
        response.setTotalCount(documents.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('ISSUER')")
    @Operation(summary = "Get document details")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String cognitoSub = jwt.getSubject();

        IssuerIdentityService.IssuerProfile issuerProfile =
                issuerIdentityService.getIssuerProfile(cognitoSub);

        String issuerId = issuerProfile.issuerId();

        Map<String, AttributeValue> item =
                documentRegistrationService.getDocumentById(documentId);

        if (item == null || item.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AttributeValue storedIssuerId = item.get("issuerId");

        if (storedIssuerId == null
                || !issuerId.equals(storedIssuerId.s())) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toDocumentResponse(item));
    }
}
