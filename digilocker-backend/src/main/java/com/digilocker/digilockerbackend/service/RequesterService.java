
package com.digilocker.digilockerbackend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Value("${app.aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public RequesterService(
            CognitoIdentityProviderClient cognitoClient,
            DynamoDbClient dynamoDbClient,
            CitizenService citizenService) {

        this.cognitoClient = cognitoClient;
        this.dynamoDbClient = dynamoDbClient;
        this.citizenService = citizenService;
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
