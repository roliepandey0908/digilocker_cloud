
package com.digilocker.digilockerbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBConfig {

    private final Region awsRegion;
    private final AwsCredentialsProvider credentialsProvider;

    public DynamoDBConfig(
            Region awsRegion,
            AwsCredentialsProvider credentialsProvider) {

        this.awsRegion = awsRegion;
        this.credentialsProvider = credentialsProvider;
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {

        return DynamoDbClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
