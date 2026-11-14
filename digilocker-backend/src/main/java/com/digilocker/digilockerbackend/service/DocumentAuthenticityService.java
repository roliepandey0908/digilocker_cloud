
package com.digilocker.digilockerbackend.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@Service
public class DocumentAuthenticityService {

    private final DynamoDbClient dynamoDbClient;
    private final S3DocumentService s3DocumentService;
    private final KmsService kmsService;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public DocumentAuthenticityService(
            DynamoDbClient dynamoDbClient,
            S3DocumentService s3DocumentService,
            KmsService kmsService) {

        this.dynamoDbClient = dynamoDbClient;
        this.s3DocumentService = s3DocumentService;
        this.kmsService = kmsService;
    }

    public record AuthenticityResult(
            boolean hashValid,
            boolean signatureValid,
            boolean documentAuthentic,
            String message) {
    }

    public AuthenticityResult verifyDocument(String documentId) {

        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("Document ID is required");
        }

        // 1. Read canonical document metadata.
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s("DOC#" + documentId).build(),
                "SK", AttributeValue.builder()
                        .s("METADATA").build()
        );

        Map<String, AttributeValue> metadata = dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(key)
                        .consistentRead(true)
                        .build()
        ).item();

        if (metadata == null || metadata.isEmpty()) {
            throw new IllegalArgumentException(
                    "Document metadata not found");
        }

        String status = getString(metadata, "status");

        if (!"ISSUED".equalsIgnoreCase(status)) {
            throw new IllegalStateException(
                    "Only issued documents can be verified");
        }

        String storedHash = getString(metadata, "sha256");
        String issuedS3Key = getString(metadata, "issuedS3Key");
        String signatureS3Key = getString(metadata, "signatureS3Key");
        String kmsKeyId = getString(metadata, "kmsKeyId");
        String signatureAlgorithm =
                getString(metadata, "signatureAlgorithm");

        if (storedHash == null || issuedS3Key == null
                || signatureS3Key == null || kmsKeyId == null
                || signatureAlgorithm == null) {
            throw new IllegalStateException(
                    "Document verification metadata is incomplete");
        }

        // 2. Retrieve issued PDF and detached signature.
        byte[] pdfBytes =
                s3DocumentService.getObjectBytes(issuedS3Key);

        byte[] signatureBytes =
                s3DocumentService.getObjectBytes(signatureS3Key);

        // 3. Recalculate SHA-256.
        byte[] calculatedDigest = calculateSha256(pdfBytes);

        String calculatedHash =
                HexFormat.of().formatHex(calculatedDigest);

        boolean hashValid =
                calculatedHash.equalsIgnoreCase(storedHash);

        // 4. Verify detached KMS signature against actual PDF digest.
        boolean signatureValid = kmsService.verifyDigest(
                calculatedDigest,
                signatureBytes,
                kmsKeyId,
                signatureAlgorithm
        );

        // 5. Both checks must pass.
        boolean authentic = hashValid && signatureValid;

        String message = authentic
                ? "Document authenticity verified successfully"
                : "Document authenticity verification failed";

        return new AuthenticityResult(
                hashValid,
                signatureValid,
                authentic,
                message
        );
    }

    private byte[] calculateSha256(byte[] documentBytes) {

        if (documentBytes == null || documentBytes.length == 0) {
            throw new IllegalArgumentException(
                    "Document content is empty");
        }

        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(documentBytes);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", ex);
        }
    }

    private String getString(
            Map<String, AttributeValue> item,
            String attributeName) {

        AttributeValue value = item.get(attributeName);

        return value == null ? null : value.s();
    }
}
