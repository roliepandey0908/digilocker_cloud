
package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

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

    // Convert dynamic metadata values into DynamoDB attributes.
    private Map<String, AttributeValue> toDynamoMetadata(
            Map<String, Object> metadata) {

        Map<String, AttributeValue> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {

            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String stringValue) {

                result.put(key, AttributeValue.fromS(stringValue));

            } else if (value instanceof Number numberValue) {

                String numericValue = new BigDecimal(
                        numberValue.toString()
                ).stripTrailingZeros().toPlainString();

                result.put(key, AttributeValue.fromN(numericValue));

            } else {

                throw new IllegalArgumentException(
                        "Unsupported metadata value for field: " + key
                );
            }
        }

        return result;
    }

    // Create a DynamoDB item with partition and sort keys.
    private Map<String, AttributeValue> createItem(
            String partitionKey,
            String sortKey,
            Map<String, AttributeValue> attributes) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", av(partitionKey));
        item.put("SK", av(sortKey));
        item.putAll(attributes);

        return item;
    }

    // Register a document using dynamic document type and metadata.
    public void registerDocument(
            String documentId,
            String issuerId,
            String documentTypeId,
            String documentTypeName,
            Map<String, Object> metadata,
            String s3Key,
            String documentUri,
            Instant uploadedAt) {

        if (metadata == null || metadata.isEmpty()) {
            throw new IllegalArgumentException(
                    "Document metadata is required."
            );
        }

        String repositorySortKey = "RECORD#" + documentId;

        Map<String, AttributeValue> documentAttributes = new HashMap<>();

        documentAttributes.put("documentId", av(documentId));
        documentAttributes.put("issuerId", av(issuerId));
        documentAttributes.put("documentTypeId", av(documentTypeId));
        documentAttributes.put("documentTypeName", av(documentTypeName));

        // Compatibility with existing response mapping.
        documentAttributes.put("documentType", av(documentTypeName));

        documentAttributes.put(
                "metadata",
                AttributeValue.builder()
                        .m(toDynamoMetadata(metadata))
                        .build()
        );

        documentAttributes.put("s3Bucket", av(bucketName));
        documentAttributes.put("s3Key", av(s3Key));
        documentAttributes.put("documentUri", av(documentUri));
        documentAttributes.put("status", av("PENDING_UPLOAD"));
        documentAttributes.put("uploadedAt", av(uploadedAt.toString()));

        Map<String, AttributeValue> issuerAttributes =
                new HashMap<>(documentAttributes);

        Map<String, AttributeValue> uriAttributes = new HashMap<>();
        uriAttributes.put("documentId", av(documentId));
        uriAttributes.put("issuerId", av(issuerId));

        Map<String, AttributeValue> canonicalItem = createItem(
                "DOC#" + documentId,
                "METADATA",
                documentAttributes
        );

        Map<String, AttributeValue> issuerItem = createItem(
                "ISSUER#" + issuerId,
                repositorySortKey,
                issuerAttributes
        );

        Map<String, AttributeValue> uriItem = createItem(
                "URI#" + documentUri,
                "DOCUMENT",
                uriAttributes
        );

        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder()
                        .transactItems(
                                conditionalPut(canonicalItem),
                                conditionalPut(issuerItem),
                                conditionalPut(uriItem)
                        )
                        .build()
        );
    }

    // Get canonical document metadata.
    public Map<String, AttributeValue> getDocumentById(String documentId) {

        return dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "PK", av("DOC#" + documentId),
                                "SK", av("METADATA")
                        ))
                        .consistentRead(true)
                        .build()
        ).item();
    }

    // Get all ISSUED documents belonging to an issuer.
    public List<Map<String, AttributeValue>> getIssuedDocumentsByIssuer(
            String issuerId) {

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

    // LEGACY: Mark a document uploaded using the old repository key.
    public void markDocumentUploaded(
            String documentId,
            String issuerId,
            String enrollmentId,
            String documentType,
            Integer passingYear,
            String s3VersionId) {

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

    // DYNAMIC: Mark a document uploaded using RECORD#<documentId>.
    public void markDocumentUploaded(
            String documentId,
            String issuerId,
            String s3VersionId) {

        if (s3VersionId == null || s3VersionId.isBlank()) {
            throw new IllegalArgumentException(
                    "S3 version ID is required"
            );
        }

        String repositorySortKey = "RECORD#" + documentId;

        Map<String, AttributeValue> values = Map.of(
                ":pending", av("PENDING_UPLOAD"),
                ":uploaded", av("UPLOADED"),
                ":versionId", av(s3VersionId)
        );

        Map<String, String> names = Map.of(
                "#status", "status"
        );

        String updateExpression =
                "SET #status = :uploaded, s3VersionId = :versionId";

        String conditionExpression = "#status = :pending";

        Update canonicalUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", av("DOC#" + documentId),
                        "SK", av("METADATA")
                ))
                .updateExpression(updateExpression)
                .conditionExpression(conditionExpression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        Update repositoryUpdate = Update.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", av("ISSUER#" + issuerId),
                        "SK", av(repositorySortKey)
                ))
                .updateExpression(updateExpression)
                .conditionExpression(conditionExpression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

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
    }

    // Update canonical metadata and issuer repository atomically.
    public void updateDocumentStatus(
            String documentId,
            String issuerId,
            String enrollmentId,
            String documentType,
            Integer passingYear,
            String expectedStatus,
            String newStatus) {

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

    // LEGACY: Register a document using enrollment ID and passing year.
    public void registerDocument(
            String documentId,
            String issuerId,
            String enrollmentId,
            String documentType,
            Integer passingYear,
            String s3Key,
            String documentUri,
            Instant uploadedAt) {

        String timestamp = uploadedAt.toString();

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

        Map<String, AttributeValue> uriItem = Map.ofEntries(
                entry("PK", "URI#" + documentUri),
                entry("SK", "DOCUMENT"),
                entry("documentId", documentId),
                entry("issuerId", issuerId)
        );

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
            String value) {

        return Map.entry(key, av(value));
    }

    private TransactWriteItem conditionalPut(
            Map<String, AttributeValue> item) {

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
