package com.digilocker.digilockerbackend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.digilocker.digilockerbackend.dto.request.CitizenDocumentSearchRequest;
import com.digilocker.digilockerbackend.dto.request.ConsentDecisionRequest;
import com.digilocker.digilockerbackend.dto.response.ConsentDecisionResponse;
import com.digilocker.digilockerbackend.dto.response.IssuerDocumentSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.Instant;

import software.amazon.awssdk.services.dynamodb.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.digilocker.digilockerbackend.dto.response.IssuerListResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Service
public class CitizenService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final DynamoDbClient dynamoDbClient;
    private final S3DocumentService s3DocumentService;

    @Value("${app.aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public CitizenService(
            CognitoIdentityProviderClient cognitoClient,
            DynamoDbClient dynamoDbClient,
            S3DocumentService s3DocumentService) {

        this.cognitoClient = cognitoClient;
        this.dynamoDbClient = dynamoDbClient;
        this.s3DocumentService = s3DocumentService;
    }

    private Map<String, AttributeValue> findDocumentRecordById(
            String documentId) {

        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest.Builder scanBuilder = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("documentId = :documentId")
                    .expressionAttributeValues(Map.of(
                            ":documentId",
                            AttributeValue.builder().s(documentId).build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                scanBuilder.exclusiveStartKey(lastEvaluatedKey);
            }

            ScanResponse response =
                    dynamoDbClient.scan(scanBuilder.build());

            if (!response.items().isEmpty()) {
                return response.items().get(0);
            }

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null &&
                !lastEvaluatedKey.isEmpty());

        return null;
    }

    public String downloadDocument(String documentId, String citizenId) {

        if (citizenId == null || citizenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Citizen identity could not be verified"
            );
        }

        if (documentId == null || documentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document ID is required"
            );
        }

        // Check whether this document is linked to this citizen.
        Map<String, AttributeValue> linkKey = Map.of(
                "PK", AttributeValue.builder()
                        .s("DOCUMENT#" + documentId)
                        .build(),
                "SK", AttributeValue.builder()
                        .s("CITIZEN_LINK")
                        .build()
        );

        Map<String, AttributeValue> link =
                dynamoDbClient.getItem(
                        software.amazon.awssdk.services.dynamodb.model.GetItemRequest
                                .builder()
                                .tableName(tableName)
                                .key(linkKey)
                                .consistentRead(true)
                                .build()
                ).item();

        if (link == null || link.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found or not linked"
            );
        }

        String linkedCitizenId = getString(link, "citizenId");

        if (!citizenId.equals(linkedCitizenId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This document is not linked to your account"
            );
        }

        // Retrieve the actual issuer document record.
        Map<String, AttributeValue> documentRecord =
                findDocumentRecordById(documentId);

        if (documentRecord == null ||
                !"ISSUED".equals(getString(documentRecord, "status"))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Issued document not found"
            );
        }

        String issuedS3Key = getString(documentRecord, "issuedS3Key");

        if (issuedS3Key == null || issuedS3Key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Issued document file not found"
            );
        }

        return s3DocumentService.generateDownloadUrl(issuedS3Key);
    }

    public com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse
    getRequests(String citizenId) {

        if (citizenId == null || citizenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Citizen identity could not be verified"
            );
        }

        List<com.digilocker.digilockerbackend.dto.response.AccessRequestResponse>
                requests = new ArrayList<>();

        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression(
                            "PK = :pk AND begins_with(SK, :sk)"
                    )
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.builder()
                                    .s("CITIZEN#" + citizenId).build(),
                            ":sk", AttributeValue.builder()
                                    .s("REQUEST#").build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                builder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response =
                    dynamoDbClient.query(builder.build());

            for (Map<String, AttributeValue> item : response.items()) {

                var request =
                        new com.digilocker.digilockerbackend.dto.response.AccessRequestResponse();

                request.setRequestId(getString(item, "requestId"));
                request.setCitizenId(getString(item, "citizenId"));
                request.setRequesterId(getString(item, "requesterId"));
                request.setDocumentId(getString(item, "documentId"));
                request.setPurpose(getString(item, "purpose"));
                request.setStatus(getString(item, "status"));


                requests.add(request);
            }

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        var result =
                new com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse();

        result.setRequests(requests);
        result.setTotalCount(requests.size());

        return result;
    }
    public ConsentDecisionResponse decideRequest(
            String requestId,
            String citizenId,
            ConsentDecisionRequest request) {

        // Validate authenticated citizen.
        if (citizenId == null || citizenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Citizen identity could not be verified"
            );
        }

        if (requestId == null || requestId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request ID is required"
            );
        }

        if (request == null || request.getDecision() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Decision is required"
            );
        }

        String decision = request.getDecision().toUpperCase();

        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVE or REJECT"
            );
        }

        // Build the request key using the authenticated citizen ID.
        Map<String, AttributeValue> requestKey = Map.of(
                "PK", AttributeValue.builder()
                        .s("CITIZEN#" + citizenId)
                        .build(),
                "SK", AttributeValue.builder()
                        .s("REQUEST#" + requestId)
                        .build()
        );

        // Retrieve the request and verify ownership.
        Map<String, AttributeValue> existingRequest =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(requestKey)
                                .consistentRead(true)
                                .build()
                ).item();

        if (existingRequest == null || existingRequest.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Request not found or does not belong to you"
            );
        }

        String currentStatus = getString(existingRequest, "status");

        if (!"PENDING".equals(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request has already been decided"
            );
        }

        Instant now = Instant.now();

        ConsentDecisionResponse result = new ConsentDecisionResponse();
        result.setRequestId(requestId);

        try {

            if ("APPROVE".equals(decision)) {

                Integer durationMinutes =
                        request.getConsentDurationMinutes();

                if (durationMinutes == null || durationMinutes <= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "A positive consent duration in minutes is required for approval"
                    );
                }

                // Calculate approval and expiry on the server.
                Instant approvalCreatedAt = Instant.now();

                Instant expiresAt = approvalCreatedAt.plus(
                        Duration.ofMinutes(durationMinutes)
                );

                Map<String, AttributeValue> values = new LinkedHashMap<>();

                values.put(":approved", AttributeValue.builder()
                        .s("APPROVED").build());

                values.put(":pending", AttributeValue.builder()
                        .s("PENDING").build());

                values.put(":now", AttributeValue.builder()
                        .s(approvalCreatedAt.toString()).build());

                values.put(":duration", AttributeValue.builder()
                        .n(durationMinutes.toString()).build());

                values.put(":expiresAt", AttributeValue.builder()
                        .s(expiresAt.toString()).build());

                dynamoDbClient.updateItem(
                        UpdateItemRequest.builder()
                                .tableName(tableName)
                                .key(requestKey)
                                .updateExpression(
                                        "SET #status = :approved, " +
                                                "updatedAt = :now, " +
                                                "approvalCreatedAt = :now, " +
                                                "consentDurationMinutes = :duration, " +
                                                "consentExpiresAt = :expiresAt"
                                )
                                .conditionExpression("#status = :pending")
                                .expressionAttributeNames(Map.of(
                                        "#status", "status"
                                ))
                                .expressionAttributeValues(values)
                                .build()
                );

                result.setDecision("APPROVE");
                result.setConsentStatus("APPROVED");
                result.setApprovalCreatedAt(approvalCreatedAt);
                result.setConsentDurationMinutes(durationMinutes);
                result.setExpiresAt(expiresAt);

            } else {

                Map<String, AttributeValue> values = Map.of(
                        ":rejected", AttributeValue.builder()
                                .s("REJECTED").build(),
                        ":pending", AttributeValue.builder()
                                .s("PENDING").build(),
                        ":now", AttributeValue.builder()
                                .s(now.toString()).build()
                );

                dynamoDbClient.updateItem(
                        UpdateItemRequest.builder()
                                .tableName(tableName)
                                .key(requestKey)
                                .updateExpression(
                                        "SET #status = :rejected, updatedAt = :now"
                                )
                                .conditionExpression("#status = :pending")
                                .expressionAttributeNames(Map.of(
                                        "#status", "status"
                                ))
                                .expressionAttributeValues(values)
                                .build()
                );

                result.setDecision("REJECT");
                result.setConsentStatus("REJECTED");
            }

        } catch (ConditionalCheckFailedException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request has already been decided"
            );
        }

        return result;
    }

    public IssuerDocumentSearchResponse getDocuments(String citizenId) {

        if (citizenId == null || citizenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Citizen identity could not be verified"
            );
        }

        List<IssuerDocumentSearchResponse.SearchDocument> documents =
                new ArrayList<>();

        Map<String, AttributeValue> lastEvaluatedKey = null;

        // Find only links belonging to the authenticated citizen.
        do {
            ScanRequest.Builder scanBuilder = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("citizenId = :citizenId")
                    .expressionAttributeValues(Map.of(
                            ":citizenId",
                            AttributeValue.builder().s(citizenId).build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                scanBuilder.exclusiveStartKey(lastEvaluatedKey);
            }

            ScanResponse response =
                    dynamoDbClient.scan(scanBuilder.build());

            for (Map<String, AttributeValue> link : response.items()) {

                String partitionKey = getString(link, "PK");

                if (partitionKey == null ||
                        !partitionKey.startsWith("DOCUMENT#")) {
                    continue;
                }

                String documentId =
                        partitionKey.substring("DOCUMENT#".length());

                // Find the corresponding issuer document record.
                Map<String, AttributeValue> documentRecord =
                        findDocumentRecordById(documentId);

                if (documentRecord == null ||
                        !"ISSUED".equals(getString(documentRecord, "status"))) {
                    continue;
                }

                String issuedS3Key =
                        getString(documentRecord, "issuedS3Key");

                if (issuedS3Key == null || issuedS3Key.isBlank()) {
                    continue;
                }

                IssuerDocumentSearchResponse.SearchDocument document =
                        new IssuerDocumentSearchResponse.SearchDocument();

                document.setDocumentId(documentId);

                String documentTypeName =
                        getString(documentRecord, "documentTypeName");

                if (documentTypeName == null) {
                    documentTypeName =
                            getString(documentRecord, "documentType");
                }

                document.setDocumentType(documentTypeName);
                document.setStatus("ISSUED");

                document.setViewUrl(
                        s3DocumentService.generateViewUrl(issuedS3Key)
                );

                document.setUrlExpiresInSeconds(300);

                documents.add(document);
            }

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null &&
                !lastEvaluatedKey.isEmpty());

        IssuerDocumentSearchResponse result =
                new IssuerDocumentSearchResponse();

        result.setDocuments(documents);
        result.setTotalCount(documents.size());

        result.setMessage(
                documents.isEmpty()
                        ? "You have no linked documents."
                        : "Linked documents retrieved successfully."
        );

        return result;
    }

    public List<Map<String, Object>> getIssuerDocumentTypes(String issuerId) {

        // Make sure the requested issuer is a registered issuer.
        boolean issuerExists = getIssuers().issuers().stream()
                .anyMatch(issuer -> issuer.issuerId().equals(issuerId));

        if (!issuerExists) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Issuer not found"
            );
        }

        List<Map<String, Object>> documentTypes = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder queryBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression(
                            "PK = :pk AND begins_with(SK, :sk)"
                    )
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.builder()
                                    .s("ISSUER#" + issuerId).build(),
                            ":sk", AttributeValue.builder()
                                    .s("DOCTYPE#").build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                queryBuilder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response =
                    dynamoDbClient.query(queryBuilder.build());

            for (Map<String, AttributeValue> item : response.items()) {

                Map<String, Object> documentType = new LinkedHashMap<>();

                documentType.put(
                        "documentTypeId",
                        item.get("documentTypeId").s()
                );

                documentType.put(
                        "documentTypeName",
                        item.get("documentTypeName").s()
                );


                documentTypes.add(documentType);
            }

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return documentTypes;
    }

    public IssuerDocumentSearchResponse getDocument(
            CitizenDocumentSearchRequest request,
            String citizenId) {

        if (citizenId == null || citizenId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Citizen identity could not be verified"
            );
        }

        // Validate issuer, document type, and configured search fields.
        Map<String, Object> typeConfig = getDocumentTypeFields(
                request.issuerId(),
                request.documentTypeId()
        );

        String documentTypeName =
                (String) typeConfig.get("documentTypeName");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configuredFields =
                (List<Map<String, Object>>) typeConfig.get("searchFields");

        Map<String, String> allowedFields = new LinkedHashMap<>();

        for (Map<String, Object> field : configuredFields) {
            allowedFields.put(
                    (String) field.get("fieldKey"),
                    (String) field.get("dataType")
            );
        }

        // Validate submitted search criteria.
        for (Map.Entry<String, Object> entry :
                request.searchCriteria().entrySet()) {

            String fieldKey = entry.getKey();

            if (!allowedFields.containsKey(fieldKey)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown search field: " + fieldKey
                );
            }

            validateSearchValue(
                    fieldKey,
                    allowedFields.get(fieldKey),
                    entry.getValue()
            );
        }

        // Find matching issued documents.
        List<Map<String, AttributeValue>> matches = new ArrayList<>();

        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder queryBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression(
                            "PK = :pk AND begins_with(SK, :sk)"
                    )
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.builder()
                                    .s("ISSUER#" + request.issuerId())
                                    .build(),
                            ":sk", AttributeValue.builder()
                                    .s("RECORD#")
                                    .build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                queryBuilder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response =
                    dynamoDbClient.query(queryBuilder.build());

            for (Map<String, AttributeValue> item : response.items()) {

                if (!"ISSUED".equals(getString(item, "status"))) {
                    continue;
                }

                if (!request.documentTypeId().equals(
                        getString(item, "documentTypeId"))) {
                    continue;
                }

                AttributeValue metadataAttribute = item.get("metadata");

                if (metadataAttribute == null ||
                        metadataAttribute.m() == null) {
                    continue;
                }

                if (!matchesSearchCriteria(
                        metadataAttribute.m(),
                        request.searchCriteria())) {
                    continue;
                }

                if (getString(item, "issuedS3Key") == null) {
                    continue;
                }

                matches.add(item);
            }

            lastEvaluatedKey = response.lastEvaluatedKey();

        } while (lastEvaluatedKey != null &&
                !lastEvaluatedKey.isEmpty());

        IssuerDocumentSearchResponse result =
                new IssuerDocumentSearchResponse();

        result.setDocuments(new ArrayList<>());
        result.setTotalCount(0);

        if (matches.isEmpty()) {
            result.setMessage("No matching issued document was found.");
            return result;
        }

        if (matches.size() > 1) {
            result.setMessage(
                    "Multiple documents matched. Please provide more search details."
            );
            return result;
        }

        // Exactly one document matched.
        Map<String, AttributeValue> matchedDocument = matches.get(0);

        String documentId = getString(matchedDocument, "documentId");
        String issuedS3Key = getString(matchedDocument, "issuedS3Key");

        if (documentId == null || documentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The matched document has no document ID"
            );
        }

        // Atomically link the document to this citizen.
        Map<String, AttributeValue> linkKey = Map.of(
                "PK", AttributeValue.builder()
                        .s("DOCUMENT#" + documentId)
                        .build(),
                "SK", AttributeValue.builder()
                        .s("CITIZEN_LINK")
                        .build()
        );

        try {
            Map<String, AttributeValue> linkItem = new LinkedHashMap<>(
                    linkKey
            );

            linkItem.put("citizenId",
                    AttributeValue.builder().s(citizenId).build());

            linkItem.put("linkedAt",
                    AttributeValue.builder().s(Instant.now().toString()).build());

            dynamoDbClient.putItem(
                    PutItemRequest.builder()
                            .tableName(tableName)
                            .item(linkItem)
                            .conditionExpression(
                                    "attribute_not_exists(PK) AND attribute_not_exists(SK)"
                            )
                            .build()
            );

        } catch (ConditionalCheckFailedException ex) {

            // A link already exists. Check who owns it.
            Map<String, AttributeValue> existingLink =
                    dynamoDbClient.getItem(
                            software.amazon.awssdk.services.dynamodb.model.GetItemRequest
                                    .builder()
                                    .tableName(tableName)
                                    .key(linkKey)
                                    .consistentRead(true)
                                    .build()
                    ).item();

            String existingCitizenId =
                    existingLink != null && existingLink.containsKey("citizenId")
                            ? existingLink.get("citizenId").s()
                            : null;

            if (!citizenId.equals(existingCitizenId)) {
                result.setMessage(
                        "This document is already linked to another citizen."
                );
                return result;
            }

            // It is already linked to this same citizen.
            // Allow the citizen to view it again.
        }

        // Generate a short-lived URL only after ownership is confirmed.
        IssuerDocumentSearchResponse.SearchDocument document =
                new IssuerDocumentSearchResponse.SearchDocument();

        document.setDocumentId(documentId);
        document.setDocumentType(documentTypeName);
        document.setStatus("ISSUED");
        document.setViewUrl(
                s3DocumentService.generateViewUrl(issuedS3Key)
        );
        document.setUrlExpiresInSeconds(300);

        result.setDocuments(List.of(document));
        result.setTotalCount(1);
        result.setMessage("Document successfully linked to your account.");

        return result;
    }


    private void validateSearchValue(
            String fieldKey,
            String dataType,
            Object value) {

        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search value cannot be null: " + fieldKey
            );
        }

        try {
            switch (dataType) {
                case "STRING" -> {
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException();
                    }
                }

                case "DATE" -> {
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException();
                    }

                    LocalDate.parse((String) value);
                }

                case "NUMBER" -> new BigDecimal(value.toString());

                default -> throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported search field type: " + dataType
                );
            }

        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid value for search field: " + fieldKey
            );
        }
    }


    private boolean matchesSearchCriteria(
            Map<String, AttributeValue> metadata,
            Map<String, Object> criteria) {

        for (Map.Entry<String, Object> entry : criteria.entrySet()) {

            AttributeValue storedValue = metadata.get(entry.getKey());

            if (storedValue == null) {
                return false;
            }

            Object searchValue = entry.getValue();

            if (storedValue.s() != null) {

                if (!storedValue.s().equals(searchValue.toString())) {
                    return false;
                }

            } else if (storedValue.n() != null) {

                try {
                    BigDecimal storedNumber =
                            new BigDecimal(storedValue.n());

                    BigDecimal requestedNumber =
                            new BigDecimal(searchValue.toString());

                    if (storedNumber.compareTo(requestedNumber) != 0) {
                        return false;
                    }

                } catch (NumberFormatException ex) {
                    return false;
                }

            } else {
                return false;
            }
        }

        return true;
    }


    private String getString(
            Map<String, AttributeValue> item,
            String attributeName) {

        AttributeValue value = item.get(attributeName);

        return value != null ? value.s() : null;
    }

    public Map<String, Object> getDocumentTypeFields(
            String issuerId,
            String documentTypeId) {

        // Verify that the issuer exists.
        boolean issuerExists = getIssuers().issuers().stream()
                .anyMatch(issuer -> issuer.issuerId().equals(issuerId));

        if (!issuerExists) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Issuer not found"
            );
        }

        // Fetch the selected document type.
        Map<String, AttributeValue> item = dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "PK", AttributeValue.builder()
                                        .s("ISSUER#" + issuerId).build(),
                                "SK", AttributeValue.builder()
                                        .s("DOCTYPE#" + documentTypeId).build()
                        ))
                        .consistentRead(true)
                        .build()
        ).item();

        if (item == null || item.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Document type not found for this issuer"
            );
        }

        List<Map<String, Object>> searchFields = new ArrayList<>();

        AttributeValue configuredFields = item.get("searchFields");

        if (configuredFields == null || configuredFields.l() == null) {
            throw new IllegalStateException(
                    "Document type has no valid field configuration"
            );
        }

        for (AttributeValue fieldValue : configuredFields.l()) {

            Map<String, AttributeValue> field = fieldValue.m();

            Map<String, Object> searchField = new LinkedHashMap<>();

            searchField.put("fieldKey", field.get("fieldKey").s());
            searchField.put("displayName", field.get("displayName").s());
            searchField.put("dataType", field.get("dataType").s());
            searchField.put("required", field.get("required").bool());

            searchFields.add(searchField);
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("documentTypeId", item.get("documentTypeId").s());
        result.put("documentTypeName", item.get("documentTypeName").s());
        result.put("searchFields", searchFields);

        return result;
    }


    public IssuerListResponse getIssuers() {

        Map<String, IssuerListResponse.IssuerSummary> uniqueIssuers =
                new LinkedHashMap<>();

        String nextToken = null;

        do {
            ListUsersInGroupRequest.Builder requestBuilder =
                    ListUsersInGroupRequest.builder()
                            .userPoolId(userPoolId)
                            .groupName("ISSUER")
                            .limit(60);

            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            ListUsersInGroupResponse response =
                    cognitoClient.listUsersInGroup(requestBuilder.build());

            for (UserType user : response.users()) {

                String cognitoSub = null;
                String fullName = null;

                for (AttributeType attribute : user.attributes()) {

                    if ("sub".equals(attribute.name())) {
                        cognitoSub = attribute.value();
                    }

                    if ("name".equals(attribute.name())) {
                        fullName = attribute.value();
                    }
                }

                if (cognitoSub == null || fullName == null
                        || fullName.isBlank()) {
                    continue;
                }

                // Find the issuer's internal profile in DynamoDB.
                Map<String, AttributeValue> key = Map.of(
                        "PK", AttributeValue.builder()
                                .s("USER#" + cognitoSub)
                                .build(),
                        "SK", AttributeValue.builder()
                                .s("PROFILE")
                                .build()
                );

                Map<String, AttributeValue> profile =
                        dynamoDbClient.getItem(
                                GetItemRequest.builder()
                                        .tableName(tableName)
                                        .key(key)
                                        .consistentRead(true)
                                        .build()
                        ).item();

                if (profile == null || profile.isEmpty()
                        || !profile.containsKey("issuerId")) {
                    continue;
                }

                String issuerId = profile.get("issuerId").s();

                if (issuerId == null || issuerId.isBlank()) {
                    continue;
                }

                uniqueIssuers.putIfAbsent(
                        issuerId,
                        new IssuerListResponse.IssuerSummary(
                                issuerId,
                                fullName
                        )
                );
            }

            nextToken = response.nextToken();

        } while (nextToken != null);

        return new IssuerListResponse(
                new ArrayList<>(uniqueIssuers.values())
        );
    }
}