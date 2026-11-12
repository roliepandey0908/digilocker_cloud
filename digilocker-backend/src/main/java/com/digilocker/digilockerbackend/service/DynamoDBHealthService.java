package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

@Service
public class DynamoDBHealthService {

    private final DynamoDbClient dynamoDbClient;

    @Value("${app.aws.dynamodb.table-name}")
    private String tableName;

    public DynamoDBHealthService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public String checkConnection() {

        var response = dynamoDbClient.describeTable(
                DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build()
        );

        return "DynamoDB Connected Successfully! Table: "
                + response.table().tableName()
                + " | Status: "
                + response.table().tableStatusAsString();
    }
}
