
package com.digilocker.digilockerbackend.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.digilocker.digilockerbackend.domain.model.Role;
import com.digilocker.digilockerbackend.dto.request.AdminCreateCitizenRequest;
import com.digilocker.digilockerbackend.dto.request.AdminCreateIssuerRequest;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import com.digilocker.digilockerbackend.dto.request.AdminCreateRequesterRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
@Service
public class AdminUserService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final DynamoDbClient dynamoDbClient;

    @Value("${app.aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public AdminUserService(
            CognitoIdentityProviderClient cognitoClient,
            DynamoDbClient dynamoDbClient) {

        this.cognitoClient = cognitoClient;
        this.dynamoDbClient = dynamoDbClient;
    }
    private void saveRequesterProfile(
            String cognitoSub) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.builder()
                .s("USER#" + cognitoSub)
                .build());

        item.put("SK", AttributeValue.builder()
                .s("PROFILE")
                .build());

        item.put("role", AttributeValue.builder()
                .s(Role.REQUESTER.name())
                .build());



        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .conditionExpression("attribute_not_exists(PK)")
                        .build()
        );
    }
    public String createRequester(AdminCreateRequesterRequest request) {

        String cognitoSub = createCognitoUser(
                request.email(),
                request.fullName(),
                Role.REQUESTER
        );

        saveRequesterProfile(cognitoSub);

        return cognitoSub;
    }
    private String generateIssuerId() {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        String randomSuffix = UUID.randomUUID()
                .toString()
                .substring(0, 3)
                .toUpperCase();

        return "UNI-" + timestamp + "-" + randomSuffix;
    }
    public String createCitizen(AdminCreateCitizenRequest request) {

        String cognitoSub = createCognitoUser(
                request.email(),
                request.fullName(),
                Role.CITIZEN
        );

        saveCitizenProfile(cognitoSub);

        return cognitoSub;
    }

    public String createIssuer(AdminCreateIssuerRequest request) {

        String issuerId = generateIssuerId();
        String uriPrefix = normalizeUriPrefix(request.uriPrefix());

        String cognitoSub = createCognitoUser(
                request.email(),
                request.fullName(),
                Role.ISSUER
        );

        saveIssuerProfile(cognitoSub, issuerId, uriPrefix);

        return cognitoSub;
    }

    private String createCognitoUser(
            String email,
            String fullName,
            Role role) {

        var createRequest =
                software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
                        .builder()
                        .userPoolId(userPoolId)
                        .username(email)
                        .userAttributes(
                                AttributeType.builder()
                                        .name("email")
                                        .value(email)
                                        .build(),
                                AttributeType.builder()
                                        .name("name")
                                        .value(fullName)
                                        .build()
                        )
                        .build();

        AdminCreateUserResponse response =
                cognitoClient.adminCreateUser(createRequest);

        cognitoClient.adminAddUserToGroup(
                AdminAddUserToGroupRequest.builder()
                        .userPoolId(userPoolId)
                        .username(email)
                        .groupName(role.name())
                        .build()
        );

        return response.user().attributes().stream()
                .filter(attribute -> attribute.name().equals("sub"))
                .map(AttributeType::value)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Cognito user sub was not returned"));
    }

    private void saveCitizenProfile(String cognitoSub) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.builder()
                .s("USER#" + cognitoSub)
                .build());

        item.put("SK", AttributeValue.builder()
                .s("PROFILE")
                .build());

        item.put("role", AttributeValue.builder()
                .s(Role.CITIZEN.name())
                .build());

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .conditionExpression("attribute_not_exists(PK)")
                        .build()
        );
    }

    private void saveIssuerProfile(
            String cognitoSub,
            String issuerId,
            String uriPrefix) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.builder()
                .s("USER#" + cognitoSub)
                .build());

        item.put("SK", AttributeValue.builder()
                .s("PROFILE")
                .build());

        item.put("role", AttributeValue.builder()
                .s(Role.ISSUER.name())
                .build());

        item.put("issuerId", AttributeValue.builder()
                .s(issuerId)
                .build());

        item.put("uriPrefix", AttributeValue.builder()
                .s(uriPrefix)
                .build());

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .conditionExpression("attribute_not_exists(PK)")
                        .build()
        );
    }

    private String normalizeUriPrefix(String uriPrefix) {

        if (uriPrefix == null || uriPrefix.isBlank()) {
            throw new IllegalArgumentException("URI prefix is required");
        }

        String normalized = uriPrefix.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!normalized.matches("^[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException(
                    "URI prefix can contain only letters, numbers, dots and hyphens");
        }

        return normalized;
    }
}
