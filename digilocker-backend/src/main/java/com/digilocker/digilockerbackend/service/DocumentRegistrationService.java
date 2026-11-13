
package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.Map;

@Service
public class DocumentRegistrationService {

    private final DynamoDbClient dynamoDbClient;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    public DocumentRegistrationService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }
    public Map<String, AttributeValue> getDocumentById(String documentId) {

        return dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "PK", av("DOC#" + documentId),
                                "SK", av("METADATA")
                        ))
                        .consistentRead(true)
                        .build())
                .item();
    }
    public List<Map<String, AttributeValue>> getIssuedDocumentsByIssuer(
            String issuerId
    ) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :issuerPk")
                .filterExpression("#status = :issued")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":issuerPk", av("ISSUER#" + issuerId),
                        ":issued", av("ISSUED")
                ))
                .build();

        List<Map<String, AttributeValue>> documents = new ArrayList<>();

        QueryResponse response;

        do {
            response = dynamoDbClient.query(request);

            documents.addAll(response.items());

            request = request.toBuilder()
                    .exclusiveStartKey(response.lastEvaluatedKey())
                    .build();

        } while (response.lastEvaluatedKey() != null
                && !response.lastEvaluatedKey().isEmpty());

        return documents;
    }
    public void markDocumentUploaded(
            String documentId,
            String issuerId,
            String enrollmentId,
            String documentType,
            Integer passingYear,
            String s3VersionId
    ) {
        if (s3VersionId == null || s3VersionId.isBlank()) {
            throw new IllegalArgumentException(
                    "S3 version ID is required"
            );
        }

        String repositorySortKey =
                "RECORD#"
                        + enrollmentId + "#"
                        + documentType + "#"
                        + passingYear + "#"
                        + documentId;

        // Update canonical metadata and save the exact S3 version.
        Update metadataUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", av("DOC#" + documentId),
                        "SK", av("METADATA")
                ))
                .updateExpression(
                        "SET #status = :newStatus, s3VersionId = :versionId"
                )
                .conditionExpression("#status = :expectedStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":newStatus", av("UPLOADED"),
                        ":expectedStatus", av("PENDING_UPLOAD"),
                        ":versionId", av(s3VersionId)
                ))
                .build();

        // Update issuer repository status.
        Update repositoryUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", av("ISSUER#" + issuerId),
                        "SK", av(repositorySortKey)
                ))
                .updateExpression("SET #status = :newStatus")
                .conditionExpression("#status = :expectedStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":newStatus", av("UPLOADED"),
                        ":expectedStatus", av("PENDING_UPLOAD")
                ))
                .build();

        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder()
                        .transactItems(
                                TransactWriteItem.builder()
                                        .update(metadataUpdate)
                                        .build(),
                                TransactWriteItem.builder()
                                        .update(repositoryUpdate)
                                        .build()
                        )
                        .build()
        );
    }
    // Update canonical metadata and issuer repository atomically.
    public void updateDocumentStatus(
            String documentId,
            String issuerId,
            String enrollmentId,
            String documentType,
            Integer passingYear,
            String expectedStatus,
            String newStatus
    ) {

        // Record A: Canonical document metadata
        Update metadataUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", av("DOC#" + documentId),
                        "SK", av("METADATA")
                ))
                .updateExpression("SET #status = :newStatus")
                .conditionExpression("#status = :expectedStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":newStatus", av(newStatus),
                        ":expectedStatus", av(expectedStatus)
                ))
                .build();

        // Record B: Issuer repository record
        String repositorySortKey =
                "RECORD#"
                        + enrollmentId + "#"
                        + documentType + "#"
                        + passingYear + "#"
                        + documentId;

        Update repositoryUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", av("ISSUER#" + issuerId),
                        "SK", av(repositorySortKey)
                ))
                .updateExpression("SET #status = :newStatus")
                .conditionExpression("#status = :expectedStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":newStatus", av(newStatus),
                        ":expectedStatus", av(expectedStatus)
                ))
                .build();

        // Both updates succeed or neither update is applied.
        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder()
                        .transactItems(
                                TransactWriteItem.builder()
                                        .update(metadataUpdate)
                                        .build(),
                                TransactWriteItem.builder()
                                        .update(repositoryUpdate)
                                        .build()
                        )
                        .build()
        );
    }

    // Register a new document in all three DynamoDB records.
    public void registerDocument(
            String documentId,
            String issuerId,
            String enrollmentId,
            String documentType,
            Integer passingYear,
            String s3Key,
            String documentUri,
            Instant uploadedAt
    ) {

        String timestamp = uploadedAt.toString();

        // Record A: Canonical document metadata
        Map<String, AttributeValue> metadataItem = Map.ofEntries(
                entry("PK", "DOC#" + documentId),
                entry("SK", "METADATA"),
                entry("documentId", documentId),
                entry("issuerId", issuerId),
                entry("enrollmentId", enrollmentId),
                entry("documentType", documentType),
                entry("passingYear", String.valueOf(passingYear)),
                entry("s3Bucket", bucketName),
                entry("s3Key", s3Key),
                entry("documentUri", documentUri),
                entry("status", "PENDING_UPLOAD"),
                entry("uploadedAt", timestamp)
        );

        // Record B: Issuer repository search record
        String repositorySortKey =
                "RECORD#"
                        + enrollmentId + "#"
                        + documentType + "#"
                        + passingYear + "#"
                        + documentId;

        Map<String, AttributeValue> repositoryItem = Map.ofEntries(
                entry("PK", "ISSUER#" + issuerId),
                entry("SK", repositorySortKey),
                entry("documentId", documentId),
                entry("issuerId", issuerId),
                entry("enrollmentId", enrollmentId),
                entry("documentType", documentType),
                entry("passingYear", String.valueOf(passingYear)),
                entry("documentUri", documentUri),
                entry("status", "PENDING_UPLOAD"),
                entry("uploadedAt", timestamp)
        );

        // Record C: Stable URI lookup
        Map<String, AttributeValue> uriItem = Map.ofEntries(
                entry("PK", "URI#" + documentUri),
                entry("SK", "DOCUMENT"),
                entry("documentId", documentId),
                entry("issuerId", issuerId)
        );

        // Save all three records in one DynamoDB transaction.
        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder()
                        .transactItems(
                                conditionalPut(metadataItem),
                                conditionalPut(repositoryItem),
                                conditionalPut(uriItem)
                        )
                        .build()
        );
    }

    private AttributeValue av(String value) {
        return AttributeValue.builder()
                .s(value)
                .build();
    }

    private Map.Entry<String, AttributeValue> entry(
            String key,
            String value
    ) {
        return Map.entry(key, av(value));
    }

    private TransactWriteItem conditionalPut(
            Map<String, AttributeValue> item
    ) {

        Put put = Put.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression(
                        "attribute_not_exists(PK) AND attribute_not_exists(SK)"
                )
                .build();

        return TransactWriteItem.builder()
                .put(put)
                .build();
    }
}
