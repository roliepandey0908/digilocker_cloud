
package com.digilocker.digilockerbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Configuration
public class AwsConfig {

    // Read AWS region from application properties
    @Value("${app.aws.region:ap-south-1}")
    private String awsRegion;

    // Configure AWS Region
    @Bean
    public Region awsRegion() {
        return Region.of(awsRegion);
    }

    // Configure AWS credentials provider
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }
}
