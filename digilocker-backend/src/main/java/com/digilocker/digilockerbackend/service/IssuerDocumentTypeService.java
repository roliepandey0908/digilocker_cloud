
package com.digilocker.digilockerbackend.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.digilocker.digilockerbackend.dto.request.IssuerDocumentTypeRequest;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class IssuerDocumentTypeService {

    private final DynamoDbClient dynamoDbClient;
    private final IssuerIdentityService issuerIdentityService;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    private static final Set<String> SUPPORTED_TYPES =
            Set.of("STRING", "DATE", "NUMBER");

    public IssuerDocumentTypeService(
            DynamoDbClient dynamoDbClient,
            IssuerIdentityService issuerIdentityService) {

        this.dynamoDbClient = dynamoDbClient;
        this.issuerIdentityService = issuerIdentityService;
    }
    public String validateDocumentMetadata(
            String cognitoSub,
            String documentTypeId,
            Map<String, Object> metadata) {

        String issuerId = issuerIdentityService.getIssuerId(cognitoSub);

        if (metadata == null || metadata.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document metadata is required"
            );
        }

        // Fetch the selected document type from this issuer's partition.
        Map<String, AttributeValue> item = dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "PK", av("ISSUER#" + issuerId),
                                "SK", av("DOCTYPE#" + documentTypeId)
                        ))
                        .consistentRead(true)
                        .build()
        ).item();

        if (item == null || item.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document type not found for this issuer"
            );
        }

        Set<String> allowedFields = new HashSet<>();

        AttributeValue configuredFields = item.get("searchFields");

        if (configuredFields == null || configuredFields.l() == null) {
            throw new IllegalStateException(
                    "Document type has no valid field configuration"
            );
        }

        for (AttributeValue fieldValue : configuredFields.l()) {

            Map<String, AttributeValue> field = fieldValue.m();

            String fieldKey = field.get("fieldKey").s();
            String dataType = field.get("dataType").s();
            boolean required = field.get("required").bool();

            allowedFields.add(fieldKey);

            Object value = metadata.get(fieldKey);

            // Check required fields.
            if (required && (value == null
                    || (value instanceof String s && s.isBlank()))) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Required field is missing: " + fieldKey
                );
            }

            if (value == null) {
                continue;
            }

            // Validate according to the configured data type.
            switch (dataType.toUpperCase(Locale.ROOT)) {

                case "STRING" -> {
                    if (!(value instanceof String)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldKey + " must be a string"
                        );
                    }
                }

                case "NUMBER" -> {
                    if (!(value instanceof Number)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldKey + " must be a number"
                        );
                    }
                }

                case "DATE" -> {
                    if (!(value instanceof String dateValue)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldKey + " must be a date in yyyy-MM-dd format"
                        );
                    }

                    try {
                        LocalDate.parse(dateValue);
                    } catch (DateTimeParseException ex) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldKey + " must be a valid date in yyyy-MM-dd format"
                        );
                    }
                }

                default -> throw new IllegalStateException(
                        "Unsupported configured data type: " + dataType
                );
            }
        }

        // Reject fields that were not configured by this issuer.
        for (String submittedField : metadata.keySet()) {

            if (!allowedFields.contains(submittedField)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unrecognized metadata field: " + submittedField
                );
            }
        }

        return item.get("documentTypeName").s();
    }
    public List<Map<String, Object>> getDocumentTypes(String cognitoSub) {

        String issuerId = issuerIdentityService.getIssuerId(cognitoSub);

        List<Map<String, Object>> documentTypes = new ArrayList<>();

        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder queryBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                    .expressionAttributeValues(Map.of(
                            ":pk", av("ISSUER#" + issuerId),
                            ":sk", av("DOCTYPE#")
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                queryBuilder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response =
                    dynamoDbClient.query(queryBuilder.build());

            for (Map<String, AttributeValue> item : response.items()) {

                Map<String, Object> result = new HashMap<>();

                result.put("documentTypeId", item.get("documentTypeId").s());
                result.put("documentTypeName", item.get("documentTypeName").s());

                documentTypes.add(result);
            }

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return documentTypes;
    }

    public String registerDocumentType(
            String cognitoSub,
            IssuerDocumentTypeRequest request) {

        String issuerId =
                issuerIdentityService.getIssuerId(cognitoSub);

        Set<String> fieldKeys = new HashSet<>();

        for (IssuerDocumentTypeRequest.SearchField field
                : request.searchFields()) {

            String dataType =
                    field.dataType().toUpperCase(Locale.ROOT);

            if (!SUPPORTED_TYPES.contains(dataType)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported field data type: " + dataType);
            }

            if (!fieldKeys.add(field.fieldKey())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate search field: " + field.fieldKey());
            }
        }

        String documentTypeId =
                request.documentTypeName()
                        .trim()
                        .toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");

        if (documentTypeId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document type name");
        }

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", av("ISSUER#" + issuerId));
        item.put("SK", av("DOCTYPE#" + documentTypeId));
        item.put("documentTypeId", av(documentTypeId));
        item.put("documentTypeName",
                av(request.documentTypeName().trim()));

        item.put("searchFields",
                AttributeValue.builder()
                        .l(request.searchFields().stream()
                                .map(field -> AttributeValue.builder()
                                        .m(Map.of(
                                                "fieldKey", av(field.fieldKey()),
                                                "displayName", av(field.displayName()),
                                                "dataType", av(
                                                        field.dataType().toUpperCase(Locale.ROOT)),
                                                "required", AttributeValue.builder()
                                                        .bool(field.required())
                                                        .build()
                                        ))
                                        .build())
                                .toList())
                        .build());

        item.put("createdAt", av(java.time.Instant.now().toString()));

        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression(
                            "attribute_not_exists(PK) AND attribute_not_exists(SK)")
                    .build());
        } catch (ConditionalCheckFailedException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This document type is already registered");
        }

        return documentTypeId;
    }

    private AttributeValue av(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
