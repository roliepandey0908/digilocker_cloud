
package com.digilocker.digilockerbackend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.digilocker.digilockerbackend.dto.response.AccessRequestListResponse;
import com.digilocker.digilockerbackend.dto.response.RequesterVerificationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.digilocker.digilockerbackend.dto.request.AccessRequestCreateRequest;
import com.digilocker.digilockerbackend.dto.response.AccessRequestResponse;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@Service
public class RequesterService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final DynamoDbClient dynamoDbClient;
    private final CitizenService citizenService;
    private final DocumentAuthenticityService documentAuthenticityService;
    private final S3DocumentService s3DocumentService;
    @Value("${app.aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public RequesterService(
            CognitoIdentityProviderClient cognitoClient,
            DynamoDbClient dynamoDbClient,
            CitizenService citizenService,
            DocumentAuthenticityService documentAuthenticityService,
            S3DocumentService s3DocumentService) {

        this.cognitoClient = cognitoClient;
        this.dynamoDbClient = dynamoDbClient;
        this.citizenService = citizenService;
        this.documentAuthenticityService = documentAuthenticityService;
        this.s3DocumentService = s3DocumentService;
    }

    private String getIssuedS3Key(String documentId) {

        Map<String, AttributeValue> metadata =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(Map.of(
                                        "PK", AttributeValue.builder()
                                                .s("DOC#" + documentId).build(),
                                        "SK", AttributeValue.builder()
                                                .s("METADATA").build()
                                ))
                                .consistentRead(true)
                                .build()
                ).item();

        if (metadata == null || metadata.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document metadata not found");
        }

        String issuedS3Key = getString(metadata, "issuedS3Key");

        if (issuedS3Key == null || issuedS3Key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Issued document location is missing");
        }

        return issuedS3Key;
    }

    public RequesterVerificationResponse verifyRequest(
            String requestId,
            String requesterId) {

        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Requester identity could not be verified");
        }

        if (requestId == null || requestId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request ID is required");
        }

        // 1. Find this request under the authenticated requester.
        QueryResponse query = dynamoDbClient.query(
                QueryRequest.builder()
                        .tableName(tableName)
                        .indexName("GSI1")
                        .keyConditionExpression(
                                "GSI1PK = :requester AND GSI1SK = :request")
                        .expressionAttributeValues(Map.of(
                                ":requester",
                                AttributeValue.builder()
                                        .s("REQUESTER#" + requesterId).build(),
                                ":request",
                                AttributeValue.builder()
                                        .s("REQUEST#" + requestId).build()
                        ))
                        .build()
        );

        if (query.items().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Request not found for this requester");
        }

        // 2. Get the citizen's primary key from the indexed record.
        String primaryKey = getString(query.items().get(0), "PK");

        if (primaryKey == null || !primaryKey.startsWith("CITIZEN#")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid access request record");
        }

        String citizenId = primaryKey.substring("CITIZEN#".length());

        // 3. Read the authoritative request record consistently.
        Map<String, AttributeValue> requestItem =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(Map.of(
                                        "PK", AttributeValue.builder()
                                                .s("CITIZEN#" + citizenId).build(),
                                        "SK", AttributeValue.builder()
                                                .s("REQUEST#" + requestId).build()
                                ))
                                .consistentRead(true)
                                .build()
                ).item();

        if (requestItem == null || requestItem.isEmpty()
                || !requesterId.equals(getString(requestItem, "requesterId"))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Request not found for this requester");
        }

        String documentId = getString(requestItem, "documentId");
        String status = getString(requestItem, "status");

        RequesterVerificationResponse response =
                new RequesterVerificationResponse();

        response.setRequestId(requestId);
        response.setDocumentId(documentId);

        // 4. Handle pending and rejected requests.
        if ("PENDING".equalsIgnoreCase(status)) {
            response.setStatus("PENDING");
            response.setMessage("Waiting for citizen approval");
            return response;
        }

        if ("REJECTED".equalsIgnoreCase(status)) {
            response.setStatus("REJECTED");
            response.setMessage("Citizen rejected this access request");
            return response;
        }

        if (!"APPROVED".equalsIgnoreCase(status)) {
            response.setStatus(status);
            response.setMessage("This request is not approved for access");
            return response;
        }

        // 5. Check consent expiry.
        String expiryValue = getString(requestItem, "consentExpiresAt");

        if (expiryValue == null || expiryValue.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Consent expiry information is missing");
        }

        Instant now = Instant.now();
        Instant consentExpiry;

        try {
            consentExpiry = Instant.parse(expiryValue);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid consent expiry information");
        }

        response.setConsentExpiresAt(consentExpiry);

        long remainingSeconds =
                Duration.between(now, consentExpiry).getSeconds();

        if (remainingSeconds <= 0) {
            response.setStatus("EXPIRED");
            response.setRemainingConsentSeconds(0L);
            response.setMessage(
                    "Consent period expired. Please submit a new request to the citizen.");
            return response;
        }

        response.setRemainingConsentSeconds(remainingSeconds);

        // 6. Verify PDF hash and detached KMS signature.
        DocumentAuthenticityService.AuthenticityResult authenticity =
                documentAuthenticityService.verifyDocument(documentId);

        response.setHashValid(authenticity.hashValid());
        response.setSignatureValid(authenticity.signatureValid());
        response.setDocumentAuthentic(authenticity.documentAuthentic());

        if (!authenticity.documentAuthentic()) {
            response.setStatus("AUTHENTICITY_FAILED");
            response.setMessage(
                    "Document authenticity verification failed. Viewing access was not granted.");
            return response;
        }

        // 7. Generate URL for no longer than the remaining consent period.
        long maximumUrlSeconds = Duration.ofDays(7).getSeconds();

        long urlValiditySeconds = Math.min(
                remainingSeconds,
                maximumUrlSeconds
        );

        Instant viewUrlExpiry = now.plusSeconds(urlValiditySeconds);

        String viewUrl = s3DocumentService.generateViewUrl(
                getIssuedS3Key(documentId),
                Duration.ofSeconds(urlValiditySeconds)
        );

        response.setStatus("APPROVED");
        response.setViewUrl(viewUrl);
        response.setViewUrlExpiresAt(viewUrlExpiry);
        response.setMessage(
                "Consent is valid and document authenticity has been verified");

        return response;
    }



    public AccessRequestListResponse getRequests(String requesterId) {

        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Requester identity could not be verified"
            );
        }

        List<AccessRequestResponse> requests = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder queryBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("GSI1")
                    .keyConditionExpression("GSI1PK = :requester")
                    .expressionAttributeValues(Map.of(
                            ":requester",
                            AttributeValue.builder()
                                    .s("REQUESTER#" + requesterId)
                                    .build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                queryBuilder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse result = dynamoDbClient.query(queryBuilder.build());

            for (Map<String, AttributeValue> item : result.items()) {

                AccessRequestResponse response = new AccessRequestResponse();

                response.setRequestId(getString(item, "requestId"));
                response.setCitizenId(getString(item, "citizenId"));
                response.setRequesterId(getString(item, "requesterId"));
                response.setDocumentId(getString(item, "documentId"));
                response.setPurpose(getString(item, "purpose"));
                response.setStatus(getString(item, "status"));

                String createdAt = getString(item, "createdAt");
                String updatedAt = getString(item, "updatedAt");

                if (createdAt != null) {
                    response.setCreatedAt(Instant.parse(createdAt));
                }

                if (updatedAt != null) {
                    response.setUpdatedAt(Instant.parse(updatedAt));
                }

                requests.add(response);
            }

            lastEvaluatedKey = result.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        AccessRequestListResponse response = new AccessRequestListResponse();
        response.setRequests(requests);
        response.setTotalCount(requests.size());

        return response;
    }




    public AccessRequestResponse createRequest(
            AccessRequestCreateRequest request,
            String requesterId) {

        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Requester identity could not be verified"
            );
        }

        // 1. Validate issuer.
        boolean issuerExists = citizenService.getIssuers()
                .issuers()
                .stream()
                .anyMatch(issuer ->
                        issuer.issuerId().equals(request.getIssuerId()));

        if (!issuerExists) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Issuer not found"
            );
        }

        // 2. Validate document type belongs to this issuer.
        citizenService.getDocumentTypeFields(
                request.getIssuerId(),
                request.getDocumentTypeId()
        );

        // 3. Validate citizen belongs to the CITIZEN Cognito group.
        if (!citizenExists(request.getCitizenId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Citizen not found"
            );
        }

        // 4. Find the issued document linked to this citizen.
        String documentId = findLinkedIssuedDocument(
                request.getIssuerId(),
                request.getDocumentTypeId(),
                request.getCitizenId()
        );

        // 5. Create the pending access request.
        String requestId = "REQ-" +
                UUID.randomUUID().toString().replace("-", "").toUpperCase();

        String now = Instant.now().toString();

        Map<String, AttributeValue> item = new LinkedHashMap<>();

        item.put("PK", AttributeValue.builder()
                .s("CITIZEN#" + request.getCitizenId()).build());

        item.put("SK", AttributeValue.builder()
                .s("REQUEST#" + requestId).build());

        // GSI1: allows querying requests by authenticated requester.
        item.put("GSI1PK", AttributeValue.builder()
                .s("REQUESTER#" + requesterId).build());

        item.put("GSI1SK", AttributeValue.builder()
                .s("REQUEST#" + requestId).build());

        item.put("requestId", AttributeValue.builder()
                .s(requestId).build());

        item.put("citizenId", AttributeValue.builder()
                .s(request.getCitizenId()).build());

        item.put("requesterId", AttributeValue.builder()
                .s(requesterId).build());

        item.put("issuerId", AttributeValue.builder()
                .s(request.getIssuerId()).build());

        item.put("documentTypeId", AttributeValue.builder()
                .s(request.getDocumentTypeId()).build());

        item.put("documentId", AttributeValue.builder()
                .s(documentId).build());

        item.put("purpose", AttributeValue.builder()
                .s(request.getReason()).build());

        item.put("status", AttributeValue.builder()
                .s("PENDING").build());

        item.put("createdAt", AttributeValue.builder()
                .s(now).build());

        item.put("updatedAt", AttributeValue.builder()
                .s(now).build());

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .conditionExpression(
                                "attribute_not_exists(PK) AND attribute_not_exists(SK)"
                        )
                        .build()
        );

        AccessRequestResponse response = new AccessRequestResponse();
        Instant timestamp = Instant.parse(now);

        response.setCreatedAt(timestamp);
        response.setUpdatedAt(timestamp);
        response.setRequestId(requestId);
        response.setCitizenId(request.getCitizenId());
        response.setRequesterId(requesterId);
        response.setDocumentId(documentId);
        response.setPurpose(request.getReason());
        response.setStatus("PENDING");

        return response;
    }


    private boolean citizenExists(String citizenId) {

        String nextToken = null;

        do {
            ListUsersInGroupRequest.Builder builder =
                    ListUsersInGroupRequest.builder()
                            .userPoolId(userPoolId)
                            .groupName("CITIZEN")
                            .limit(60);

            if (nextToken != null) {
                builder.nextToken(nextToken);
            }

            ListUsersInGroupResponse response =
                    cognitoClient.listUsersInGroup(builder.build());

            for (UserType user : response.users()) {
                boolean match = user.attributes().stream()
                        .anyMatch(attribute ->
                                "sub".equals(attribute.name())
                                        && citizenId.equals(attribute.value()));

                if (match) {
                    return true;
                }
            }

            nextToken = response.nextToken();

        } while (nextToken != null);

        return false;
    }

    private String findLinkedIssuedDocument(
            String issuerId,
            String documentTypeId,
            String citizenId) {

        List<String> matchingDocumentIds = new ArrayList<>();

        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression(
                            "PK = :pk AND begins_with(SK, :sk)"
                    )
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.builder()
                                    .s("ISSUER#" + issuerId).build(),
                            ":sk", AttributeValue.builder()
                                    .s("RECORD#").build()
                    ));

            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                builder.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse result = dynamoDbClient.query(builder.build());

            for (Map<String, AttributeValue> document : result.items()) {

                if (!"ISSUED".equals(getString(document, "status"))) {
                    continue;
                }

                if (!documentTypeId.equals(
                        getString(document, "documentTypeId"))) {
                    continue;
                }

                String documentId = getString(document, "documentId");

                if (documentId == null || documentId.isBlank()) {
                    continue;
                }

                Map<String, AttributeValue> link = dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(Map.of(
                                        "PK", AttributeValue.builder()
                                                .s("DOCUMENT#" + documentId)
                                                .build(),
                                        "SK", AttributeValue.builder()
                                                .s("CITIZEN_LINK")
                                                .build()
                                ))
                                .consistentRead(true)
                                .build()
                ).item();

                if (link != null
                        && citizenId.equals(getString(link, "citizenId"))) {
                    matchingDocumentIds.add(documentId);
                }
            }

            lastEvaluatedKey = result.lastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        if (matchingDocumentIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No issued document of this type is linked to this citizen."
            );
        }

        if (matchingDocumentIds.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Multiple matching documents found. Please contact the issuer."
            );
        }

        return matchingDocumentIds.get(0);
    }

    private String getString(
            Map<String, AttributeValue> item,
            String attributeName) {

        AttributeValue value = item.get(attributeName);

        return value != null ? value.s() : null;
    }
}
