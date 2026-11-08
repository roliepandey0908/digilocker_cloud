
package com.digilocker.digilockerbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    private final Region awsRegion;
    private final AwsCredentialsProvider credentialsProvider;

    public S3Config(
            Region awsRegion,
            AwsCredentialsProvider credentialsProvider) {

        this.awsRegion = awsRegion;
        this.credentialsProvider = credentialsProvider;
    }

    @Bean
    public S3Client s3Client() {

        return S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
