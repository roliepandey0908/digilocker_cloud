
package com.digilocker.digilockerbackend.processor.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DocumentPersistenceService {

    private static final long CLAIM_LEASE_SECONDS = 300;

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final String bucketName;
    private final String tableName;
    private final String kmsKeyId;

    public DocumentPersistenceService(
            S3Client s3Client,
            DynamoDbClient dynamoDbClient,
            String bucketName,
            String tableName,
            String kmsKeyId) {

        this.s3Client = s3Client;
        this.dynamoDbClient = dynamoDbClient;
        this.bucketName = bucketName;
        this.tableName = tableName;
        this.kmsKeyId = kmsKeyId;
    }

    // STEP A: Claim the document before processing or signing it.
    public String claimDocumentForProcessing(
            String incomingKey,
            String incomingVersionId) {

        String documentId = extractDocumentId(incomingKey);

        Map<String, AttributeValue> metadata =
                getDocumentMetadata(documentId);

        validateRegisteredObject(
                metadata,
                incomingKey,
                incomingVersionId
        );

        String issuerId = requiredAttribute(metadata, "issuerId");

        // Supports both legacy and dynamic document repository keys.
        String repositorySortKey =
                getRepositorySortKey(metadata, documentId);

        String processingToken = UUID.randomUUID().toString();

        long now = Instant.now().getEpochSecond();
        long leaseExpiresAt = now + CLAIM_LEASE_SECONDS;

        Map<String, AttributeValue> values = new HashMap<>();

        values.put(":uploaded", AttributeValue.fromS("UPLOADED"));
        values.put(":processing", AttributeValue.fromS("PROCESSING"));
        values.put(":token", AttributeValue.fromS(processingToken));
        values.put(":now", AttributeValue.fromN(Long.toString(now)));
        values.put(":lease", AttributeValue.fromN(
                Long.toString(leaseExpiresAt)));

        Map<String, String> names = Map.of(
                "#status", "status"
        );

        String condition =
                "#status = :uploaded OR "
                        + "(#status = :processing "
                        + "AND processingLeaseExpiresAt < :now)";

        Update canonicalClaim = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("DOC#" + documentId),
                        "SK", AttributeValue.fromS("METADATA")
                ))
                .updateExpression(
                        "SET #status = :processing, "
                                + "processingToken = :token, "
                                + "processingLeaseExpiresAt = :lease, "
                                + "processingStartedAt = :now"
                )
                .conditionExpression(condition)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        Update repositoryClaim = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("ISSUER#" + issuerId),
                        "SK", AttributeValue.fromS(repositorySortKey)
                ))
                .updateExpression(
                        "SET #status = :processing, "
                                + "processingToken = :token, "
                                + "processingLeaseExpiresAt = :lease, "
                                + "processingStartedAt = :now"
                )
                .conditionExpression(condition)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        try {

            dynamoDbClient.transactWriteItems(
                    TransactWriteItemsRequest.builder()
                            .transactItems(
                                    TransactWriteItem.builder()
                                            .update(canonicalClaim)
                                            .build(),
                                    TransactWriteItem.builder()
                                            .update(repositoryClaim)
                                            .build()
                            )
                            .build()
            );

            return processingToken;

        } catch (TransactionCanceledException exception) {

            Map<String, AttributeValue> latest =
                    getDocumentMetadata(documentId);

            String latestStatus =
                    requiredAttribute(latest, "status");

            if ("ISSUED".equals(latestStatus)) {
                throw new AlreadyIssuedException(
                        "Document has already been issued.");
            }

            if ("PROCESSING".equals(latestStatus)) {

                long currentLease = Long.parseLong(
                        requiredAttribute(
                                latest,
                                "processingLeaseExpiresAt"
                        )
                );

                if (currentLease > Instant.now().getEpochSecond()) {
                    throw new ProcessingInProgressException(
                            "Another invocation is processing this document.");
                }
            }

            throw exception;
        }
    }

    // STEP B: Save output only after a successful processing claim.
    public void persistProcessedDocument(
            String incomingKey,
            String incomingVersionId,
            byte[] documentBytes,
            DocumentProcessingService.ProcessedDocument processedDocument,
            String processingToken) {

        if (processingToken == null || processingToken.isBlank()) {
            throw new IllegalArgumentException(
                    "A processing token is required.");
        }

        String documentId = extractDocumentId(incomingKey);

        Map<String, AttributeValue> metadata =
                getDocumentMetadata(documentId);

        validateRegisteredObject(
                metadata,
                incomingKey,
                incomingVersionId
        );

        String issuerId = requiredAttribute(metadata, "issuerId");

        // Resolve the same repository key used when claiming the document.
        String repositorySortKey =
                getRepositorySortKey(metadata, documentId);

        if (!"PROCESSING".equals(requiredAttribute(metadata, "status"))
                || !processingToken.equals(
                requiredAttribute(metadata, "processingToken"))) {

            throw new IllegalStateException(
                    "This invocation does not own the processing claim.");
        }

        String issuedKey =
                incomingKey.replaceFirst("^incoming/", "issued/");

        String signatureKey = issuedKey + ".sig";
        String issuedAt = Instant.now().toString();

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(issuedKey)
                        .contentType("application/pdf")
                        .build(),
                RequestBody.fromBytes(documentBytes)
        );

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(signatureKey)
                        .contentType("application/octet-stream")
                        .build(),
                RequestBody.fromBytes(processedDocument.signature())
        );

        updateDocumentRecords(
                documentId,
                issuerId,
                repositorySortKey,
                processedDocument,
                issuedKey,
                signatureKey,
                issuedAt,
                processingToken
        );
    }

    private void updateDocumentRecords(
            String documentId,
            String issuerId,
            String repositorySortKey,
            DocumentProcessingService.ProcessedDocument processedDocument,
            String issuedKey,
            String signatureKey,
            String issuedAt,
            String processingToken) {

        Map<String, AttributeValue> values = new HashMap<>();

        values.put(":processing", AttributeValue.fromS("PROCESSING"));
        values.put(":issued", AttributeValue.fromS("ISSUED"));
        values.put(":token", AttributeValue.fromS(processingToken));
        values.put(":hash", AttributeValue.fromS(
                processedDocument.sha256Hex()));
        values.put(":issuedKey", AttributeValue.fromS(issuedKey));
        values.put(":signatureKey", AttributeValue.fromS(signatureKey));
        values.put(":kmsKeyId", AttributeValue.fromS(kmsKeyId));
        values.put(":algorithm", AttributeValue.fromS(
                processedDocument.signingAlgorithm()));
        values.put(":issuedAt", AttributeValue.fromS(issuedAt));

        Map<String, String> names = Map.of(
                "#status", "status"
        );

        String updateExpression =
                "SET #status = :issued, "
                        + "sha256 = :hash, "
                        + "issuedS3Key = :issuedKey, "
                        + "signatureS3Key = :signatureKey, "
                        + "kmsKeyId = :kmsKeyId, "
                        + "signatureAlgorithm = :algorithm, "
                        + "issuedAt = :issuedAt "
                        + "REMOVE processingToken, processingLeaseExpiresAt";

        String condition =
                "#status = :processing AND processingToken = :token";

        Update canonicalUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("DOC#" + documentId),
                        "SK", AttributeValue.fromS("METADATA")
                ))
                .updateExpression(updateExpression)
                .conditionExpression(condition)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        Update repositoryUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("ISSUER#" + issuerId),
                        "SK", AttributeValue.fromS(repositorySortKey)
                ))
                .updateExpression(updateExpression)
                .conditionExpression(condition)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        try {

            dynamoDbClient.transactWriteItems(
                    TransactWriteItemsRequest.builder()
                            .transactItems(
                                    TransactWriteItem.builder()
                                            .update(canonicalUpdate)
                                            .build(),
                                    TransactWriteItem.builder()
                                            .update(repositoryUpdate)
                                            .build()
                            )
                            .build()
            );

        } catch (TransactionCanceledException exception) {

            Map<String, AttributeValue> latest =
                    getDocumentMetadata(documentId);

            AttributeValue latestStatus = latest.get("status");

            if (latestStatus != null
                    && "ISSUED".equals(latestStatus.s())) {

                throw new AlreadyIssuedException(
                        "Document has already been issued.");
            }

            throw exception;
        }
    }

    /**
     * Legacy documents use:
     * RECORD#enrollmentId#documentType#passingYear#documentId
     *
     * New dynamic documents use:
     * RECORD#documentId
     */
    private String getRepositorySortKey(
            Map<String, AttributeValue> metadata,
            String documentId) {

        AttributeValue enrollmentId = metadata.get("enrollmentId");
        AttributeValue documentType = metadata.get("documentType");
        AttributeValue passingYear = metadata.get("passingYear");

        if (enrollmentId != null
                && enrollmentId.s() != null
                && documentType != null
                && documentType.s() != null
                && passingYear != null
                && passingYear.s() != null) {

            return "RECORD#" + enrollmentId.s()
                    + "#" + documentType.s()
                    + "#" + passingYear.s()
                    + "#" + documentId;
        }

        return "RECORD#" + documentId;
    }

    private void validateRegisteredObject(
            Map<String, AttributeValue> metadata,
            String incomingKey,
            String incomingVersionId) {

        String registeredBucket =
                requiredAttribute(metadata, "s3Bucket");

        String registeredKey =
                requiredAttribute(metadata, "s3Key");

        String registeredVersionId =
                requiredAttribute(metadata, "s3VersionId");

        if (!bucketName.equals(registeredBucket)
                || !incomingKey.equals(registeredKey)
                || incomingVersionId == null
                || incomingVersionId.isBlank()
                || !incomingVersionId.equals(registeredVersionId)) {

            throw new IllegalStateException(
                    "Incoming S3 object or version does not match registered document."
            );
        }
    }

    private Map<String, AttributeValue> getDocumentMetadata(
            String documentId) {

        GetItemResponse response = dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "PK", AttributeValue.fromS(
                                        "DOC#" + documentId),
                                "SK", AttributeValue.fromS("METADATA")
                        ))
                        .consistentRead(true)
                        .build()
        );

        if (!response.hasItem() || response.item().isEmpty()) {
            throw new IllegalStateException(
                    "Document metadata not found: " + documentId);
        }

        return response.item();
    }

    private String extractDocumentId(String incomingKey) {

        String[] parts = incomingKey.split("/");

        if (parts.length != 4
                || !parts[0].equals("incoming")
                || !parts[3].toLowerCase().endsWith(".pdf")) {

            throw new IllegalArgumentException(
                    "Invalid incoming document S3 key.");
        }

        String filename = parts[3];

        return filename.substring(0, filename.length() - 4);
    }

    private String requiredAttribute(
            Map<String, AttributeValue> item,
            String attributeName) {

        AttributeValue value = item.get(attributeName);

        if (value == null
                || value.s() == null
                || value.s().isBlank()) {

            throw new IllegalStateException(
                    "Missing DynamoDB attribute: " + attributeName);
        }

        return value.s();
    }

    public static class AlreadyIssuedException
            extends RuntimeException {

        public AlreadyIssuedException(String message) {
            super(message);
        }
    }

    public static class ProcessingInProgressException
            extends RuntimeException {

        public ProcessingInProgressException(String message) {
            super(message);
        }
    }
}
