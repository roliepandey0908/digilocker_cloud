package com.digilocker.digilockerbackend.controller;
import com.digilocker.digilockerbackend.dto.response.DocumentListResponse;
import com.digilocker.digilockerbackend.dto.response.DocumentResponse;
import com.digilocker.digilockerbackend.service.DocumentIdGenerator;
import com.digilocker.digilockerbackend.service.DocumentRegistrationService;
import com.digilocker.digilockerbackend.service.IssuerIdentityService;
import com.digilocker.digilockerbackend.service.S3DocumentService;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import com.digilocker.digilockerbackend.service.IssuerDocumentTypeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.digilocker.digilockerbackend.dto.request.DocumentUploadRequest;
import com.digilocker.digilockerbackend.dto.request.IssuerDocumentTypeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;


@RestController
@RequestMapping("/api/issuer")
@Validated
public class IssuerController {

    private final DocumentRegistrationService documentRegistrationService;
    private final S3DocumentService s3DocumentService;
    private final DocumentIdGenerator documentIdGenerator;
    private final IssuerIdentityService issuerIdentityService;
    private final IssuerDocumentTypeService issuerDocumentTypeService;
    public IssuerController(
            S3DocumentService s3DocumentService,
            DocumentIdGenerator documentIdGenerator,
            IssuerIdentityService issuerIdentityService,
            DocumentRegistrationService documentRegistrationService,
            IssuerDocumentTypeService issuerDocumentTypeService
    ) {
        this.s3DocumentService = s3DocumentService;
        this.documentIdGenerator = documentIdGenerator;
        this.issuerIdentityService = issuerIdentityService;
        this.documentRegistrationService = documentRegistrationService;
        this.issuerDocumentTypeService=issuerDocumentTypeService;
    }

    @PostMapping("/document-types")
    @PreAuthorize("hasRole('ISSUER')")
    @Operation(summary = "Register a document type and its search requirements")
    public ResponseEntity<Map<String, String>> registerDocumentType(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody IssuerDocumentTypeRequest request) {

        String documentTypeId =
                issuerDocumentTypeService.registerDocumentType(
                        jwt.getSubject(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Document type registered successfully",
                        "documentTypeId", documentTypeId
                ));
    }
    @GetMapping("/document-types")
    @PreAuthorize("hasRole('ISSUER')")
    @Operation(summary = "List document types registered by the issuer")
    public ResponseEntity<?> getDocumentTypes(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                issuerDocumentTypeService.getDocumentTypes(jwt.getSubject())
        );
    }

//    @PostMapping(
//            value = "/documents",
//            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
//    )
//    @PreAuthorize("hasRole('ISSUER')")
//    @Operation(summary = "Upload and register a new document")
//    public ResponseEntity<DocumentResponse> uploadDocument(
//
//            @RequestParam("enrollmentId")
//            @NotBlank
//            @Size(max = 100)
//            @Pattern(regexp = "^[A-Za-z0-9_-]+$")
//            String enrollmentId,
//
//            @RequestParam("documentType")
//            @NotBlank
//            @Pattern(regexp = "^(DEGREE|MARKSHEET)$")
//            String documentType,
//
//            @RequestParam("passingYear")
//            @NotNull
//            @Min(1950)
//            @Max(2100)
//            Integer passingYear,
//
//            @RequestPart("file")
//            MultipartFile file,
//
//            @AuthenticationPrincipal Jwt jwt
//
//    ) throws IOException {
//
//        // 1. Get authenticated Cognito user ID
//        String cognitoSub = jwt.getSubject();
//
//        // 2. Resolve issuer profile from DynamoDB
//        IssuerIdentityService.IssuerProfile issuerProfile =
//                issuerIdentityService.getIssuerProfile(cognitoSub);
//
//        String issuerId = issuerProfile.issuerId();
//
//        // 3. Generate document ID
//        String documentId =
//                documentIdGenerator.generateDocumentId();
//
//        // 4. Generate stable URI
//        String documentUri = issuerProfile.uriPrefix()
//                + "/"
//                + documentType.toLowerCase(Locale.ROOT)
//                + "/"
//                + documentId;
//
//        // 5. Record upload timestamp
//        Instant uploadedAt = Instant.now();
//
//        // 6. Register three DynamoDB records as PENDING_UPLOAD
//        String expectedS3Key =
//                "incoming/" + issuerId + "/" + documentType + "/" + documentId + ".pdf";
//
//        documentRegistrationService.registerDocument(
//                documentId,
//                issuerId,
//                enrollmentId,
//                documentType,
//                passingYear,
//                expectedS3Key,
//                documentUri,
//                uploadedAt
//        );
//
//        // 7. Upload PDF to S3
//        S3DocumentService.UploadedDocument uploadedDocument =
//                s3DocumentService.uploadIncomingDocument(
//                        file,
//                        issuerId,
//                        documentType,
//                        documentId
//                );
//
//        // 8. Mark document as UPLOADED
//        documentRegistrationService.markDocumentUploaded(
//                documentId,
//                issuerId,
//                enrollmentId,
//                documentType,
//                passingYear,
//                uploadedDocument.versionId()
//        );
//
//        // 9. Prepare response
//        DocumentResponse response = new DocumentResponse();
//
//        response.setDocumentId(documentId);
//        response.setEnrollmentId(enrollmentId);
//        response.setDocumentType(documentType);
//        response.setPassingYear(passingYear);
//        response.setIssuerId(issuerId);
//        response.setStatus("UPLOADED");
//        response.setDocumentUri(documentUri);
//
//        // Document is uploaded but not yet signed or issued.
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(response);
//    }
//@io.swagger.v3.oas.annotations.parameters.RequestBody(
//        required = true,
//        content = @Content(
//                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
//                encoding = {
//                        @Encoding(
//                                name = "request",
//                                contentType = "application/json"
//                        ),
//                        @Encoding(
//                                name = "file",
//                                contentType = "application/pdf"
//                        )
//                }
//        )
//)
//@PostMapping(
//        value = "/documents",
//        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
//)
//@PreAuthorize("hasRole('ISSUER')")
//@Operation(summary = "Upload a document using its configured document type and metadata")
//public ResponseEntity<DocumentResponse> uploadDocument(
//
//        @RequestPart("request")
//        @Valid
//        DocumentUploadRequest request,
//
//        @RequestPart("file")
//        MultipartFile file,
//
//        @AuthenticationPrincipal Jwt jwt
//
//) throws IOException {
//
//    // 1. Resolve the authenticated issuer
//    String cognitoSub = jwt.getSubject();
//
//    IssuerIdentityService.IssuerProfile issuerProfile =
//            issuerIdentityService.getIssuerProfile(cognitoSub);
//
//    String issuerId = issuerProfile.issuerId();
//
//    // 2. Validate metadata against this issuer's saved document-type configuration
//    String documentTypeName =
//            issuerDocumentTypeService.validateDocumentMetadata(
//                    cognitoSub,
//                    request.documentTypeId(),
//                    request.metadata()
//            );
//
//    // 3. Generate document ID
//    String documentId = documentIdGenerator.generateDocumentId();
//
//    // 4. Generate stable document URI
//    String documentUri = issuerProfile.uriPrefix()
//            + "/"
//            + request.documentTypeId().toLowerCase(Locale.ROOT)
//            + "/"
//            + documentId;
//
//    Instant uploadedAt = Instant.now();
//
//    // 5. Generate S3 incoming key
//    String expectedS3Key =
//            "incoming/"
//                    + issuerId
//                    + "/"
//                    + request.documentTypeId()
//                    + "/"
//                    + documentId
//                    + ".pdf";
//
//    // 6. Register document metadata in DynamoDB
//    documentRegistrationService.registerDocument(
//            documentId,
//            issuerId,
//            request.documentTypeId(),
//            documentTypeName,
//            request.metadata(),
//            expectedS3Key,
//            documentUri,
//            uploadedAt
//    );
//
//    // 7. Upload PDF to S3
//    S3DocumentService.UploadedDocument uploadedDocument =
//            s3DocumentService.uploadIncomingDocument(
//                    file,
//                    issuerId,
//                    request.documentTypeId(),
//                    documentId
//            );
//
//    // 8. Mark document as UPLOADED
//    documentRegistrationService.markDocumentUploaded(
//            documentId,
//            issuerId,
//            uploadedDocument.versionId()
//    );
//
//    // 9. Prepare response
//    DocumentResponse response = new DocumentResponse();
//
//    response.setDocumentId(documentId);
//    response.setDocumentType(documentTypeName);
//    response.setIssuerId(issuerId);
//    response.setStatus("UPLOADED");
//    response.setDocumentUri(documentUri);
//
//    return ResponseEntity
//            .status(HttpStatus.CREATED)
//            .body(response);
//}


    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = {
                            @Encoding(
                                    name = "request",
                                    contentType = "application/json"
                            ),
                            @Encoding(
                                    name = "file",
                                    contentType = "application/pdf"
                            )
                    }
            )
    )
    @PostMapping(
            value = "/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ISSUER')")
    @Operation(summary = "Upload a document using its configured document type and metadata")
    public ResponseEntity<DocumentResponse> uploadDocument(

            @RequestPart("request")
            @Valid
            DocumentUploadRequest request,

            @RequestPart("file")
            MultipartFile file,

            @AuthenticationPrincipal Jwt jwt

    ) throws IOException {

        String cognitoSub = jwt.getSubject();

        IssuerIdentityService.IssuerProfile issuerProfile =
                issuerIdentityService.getIssuerProfile(cognitoSub);

        String issuerId = issuerProfile.issuerId();

        String documentTypeName =
                issuerDocumentTypeService.validateDocumentMetadata(
                        cognitoSub,
                        request.documentTypeId(),
                        request.metadata()
                );

        String documentId = documentIdGenerator.generateDocumentId();

        String documentUri = issuerProfile.uriPrefix()
                + "/"
                + request.documentTypeId().toLowerCase(Locale.ROOT)
                + "/"
                + documentId;

        Instant uploadedAt = Instant.now();

        String expectedS3Key =
                "incoming/"
                        + issuerId
                        + "/"
                        + request.documentTypeId()
                        + "/"
                        + documentId
                        + ".pdf";

        documentRegistrationService.registerDocument(
                documentId,
                issuerId,
                request.documentTypeId(),
                documentTypeName,
                request.metadata(),
                expectedS3Key,
                documentUri,
                uploadedAt
        );

        S3DocumentService.UploadedDocument uploadedDocument =
                s3DocumentService.uploadIncomingDocument(
                        file,
                        issuerId,
                        request.documentTypeId(),
                        documentId
                );

        documentRegistrationService.markDocumentUploaded(
                documentId,
                issuerId,
                uploadedDocument.versionId()
        );

        DocumentResponse response = new DocumentResponse();

        response.setDocumentId(documentId);
        response.setDocumentType(documentTypeName);
        response.setIssuerId(issuerId);
        response.setStatus("UPLOADED");
        response.setDocumentUri(documentUri);

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
