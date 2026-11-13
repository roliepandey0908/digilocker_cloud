package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3HealthService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    public S3HealthService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String checkConnection() {
        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(bucketName)
                            .build()
            );

            return "S3 Connected Successfully! Bucket: " + bucketName;

        } catch (S3Exception e) {
            throw new RuntimeException(
                    "S3 connection failed: " + e.awsErrorDetails().errorMessage(), e
            );
        }
    }
}