
package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.Map;

@Service
public class IssuerIdentityService {

    private final DynamoDbClient dynamoDbClient;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public IssuerIdentityService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public IssuerProfile getIssuerProfile(String cognitoSub) {

        if (cognitoSub == null || cognitoSub.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user"
            );
        }

        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s("USER#" + cognitoSub)
                        .build(),
                "SK", AttributeValue.builder()
                        .s("PROFILE")
                        .build()
        );

        Map<String, AttributeValue> item =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(key)
                                .consistentRead(true)
                                .build()
                ).item();

        if (item == null || item.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Issuer profile not found"
            );
        }

        String issuerId = getAttribute(item, "issuerId");
        String uriPrefix = getAttribute(item, "uriPrefix");

        if (issuerId == null || issuerId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Issuer ID is missing from user profile"
            );
        }

        if (uriPrefix == null || uriPrefix.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Issuer URI prefix is missing from user profile"
            );
        }

        uriPrefix = uriPrefix.trim().replaceAll("/+$", "");

        if (!uriPrefix.matches("^[A-Za-z0-9.-]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid issuer URI prefix"
            );
        }

        return new IssuerProfile(issuerId, uriPrefix);
    }

    private String getAttribute(
            Map<String, AttributeValue> item,
            String attributeName
    ) {
        AttributeValue attribute = item.get(attributeName);

        if (attribute == null) {
            return null;
        }

        return attribute.s();
    }

    public record IssuerProfile(
            String issuerId,
            String uriPrefix
    ) {
    }

    // Compatibility method for any existing callers.
    public String getIssuerId(String cognitoSub) {
        return getIssuerProfile(cognitoSub).issuerId();
    }
}
