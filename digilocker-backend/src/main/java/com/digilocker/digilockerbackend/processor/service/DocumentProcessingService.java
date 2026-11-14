
package com.digilocker.digilockerbackend.processor.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DocumentProcessingService {

    private static final byte[] PDF_MAGIC_BYTES =
            "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static final String SIGNING_ALGORITHM =
            "RSASSA_PSS_SHA_256";

    private final DocumentHashService documentHashService;
    private final DocumentSigningService documentSigningService;

    public DocumentProcessingService(
            DocumentHashService documentHashService,
            DocumentSigningService documentSigningService) {

        if (documentHashService == null
                || documentSigningService == null) {
            throw new IllegalArgumentException(
                    "Processing dependencies cannot be null.");
        }

        this.documentHashService = documentHashService;
        this.documentSigningService = documentSigningService;
    }

    public ProcessedDocument processDocument(byte[] documentBytes) {

        validatePdf(documentBytes);

        // Step 1: Calculate SHA-256 digest.
        byte[] digest =
                documentHashService.calculateSha256(documentBytes);

        // Step 2: Sign the digest using AWS KMS.
        byte[] signature =
                documentSigningService.signDigest(digest);

        // Step 3: Return processing results.
        String hashHex =
                java.util.HexFormat.of().formatHex(digest);

        return new ProcessedDocument(
                hashHex,
                signature,
                SIGNING_ALGORITHM
        );
    }

    private void validatePdf(byte[] documentBytes) {

        if (documentBytes == null || documentBytes.length == 0) {
            throw new IllegalArgumentException(
                    "Document cannot be empty.");
        }

        if (documentBytes.length < PDF_MAGIC_BYTES.length
                || !Arrays.equals(
                Arrays.copyOf(
                        documentBytes,
                        PDF_MAGIC_BYTES.length),
                PDF_MAGIC_BYTES)) {

            throw new IllegalArgumentException(
                    "Uploaded file is not a valid PDF.");
        }
    }

    public record ProcessedDocument(
            String sha256Hex,
            byte[] signature,
            String signingAlgorithm
    ) {
    }


}
