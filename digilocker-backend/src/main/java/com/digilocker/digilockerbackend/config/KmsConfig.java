package com.digilocker.digilockerbackend.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration
public class KmsConfig {

    private final Region awsRegion;
    private final AwsCredentialsProvider credentialsProvider;

    public KmsConfig(
            Region awsRegion,
            AwsCredentialsProvider credentialsProvider) {

        this.awsRegion = awsRegion;
        this.credentialsProvider = credentialsProvider;
    }

    @Bean
    public KmsClient kmsClient() {

        return KmsClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
