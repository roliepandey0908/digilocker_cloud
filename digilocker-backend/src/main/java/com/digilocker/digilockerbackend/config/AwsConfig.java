
package com.digilocker.digilockerbackend.config;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Configuration
public class AwsConfig {

    @Value("${app.aws.region:ap-south-1}")
    private String awsRegion;



    @Bean
    public Region awsRegion() {
        return Region.of(awsRegion);
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Presigner s3Presigner(
            Region awsRegion,
            AwsCredentialsProvider credentialsProvider) {

        return S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
