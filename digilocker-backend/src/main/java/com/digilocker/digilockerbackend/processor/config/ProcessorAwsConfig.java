
package com.digilocker.digilockerbackend.processor.config;

import com.digilocker.digilockerbackend.processor.service.DocumentHashService;
import com.digilocker.digilockerbackend.processor.service.DocumentSigningService;
import com.digilocker.digilockerbackend.processor.service.DocumentProcessingService;
import com.digilocker.digilockerbackend.processor.service.DocumentPersistenceService;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

public final class ProcessorAwsConfig {

    private static final Region AWS_REGION =
            Region.of(requiredEnv("AWS_REGION"));

    private static final String KMS_KEY_ID =
            requiredEnv("KMS_KEY_ID");

    private static final String BUCKET_NAME =
            requiredEnv("DOCUMENTS_BUCKET");

    private static final String TABLE_NAME =
            requiredEnv("DYNAMODB_TABLE");

    private static final S3Client S3_CLIENT =
            S3Client.builder()
                    .region(AWS_REGION)
                    .build();

    private static final DynamoDbClient DYNAMODB_CLIENT =
            DynamoDbClient.builder()
                    .region(AWS_REGION)
                    .build();

    private static final KmsClient KMS_CLIENT =
            KmsClient.builder()
                    .region(AWS_REGION)
                    .build();

    private static final DocumentHashService HASH_SERVICE =
            new DocumentHashService();

    private static final DocumentSigningService SIGNING_SERVICE =
            new DocumentSigningService(KMS_CLIENT, KMS_KEY_ID);

    private static final DocumentProcessingService PROCESSING_SERVICE =
            new DocumentProcessingService(
                    HASH_SERVICE,
                    SIGNING_SERVICE
            );

    private static final DocumentPersistenceService PERSISTENCE_SERVICE =
            new DocumentPersistenceService(
                    S3_CLIENT,
                    DYNAMODB_CLIENT,
                    BUCKET_NAME,
                    TABLE_NAME,
                    KMS_KEY_ID
            );

    private ProcessorAwsConfig() {
        // Prevent instantiation.
    }

    public static S3Client s3Client() {
        return S3_CLIENT;
    }

    public static DynamoDbClient dynamoDbClient() {
        return DYNAMODB_CLIENT;
    }

    public static KmsClient kmsClient() {
        return KMS_CLIENT;
    }

    public static DocumentProcessingService documentProcessingService() {
        return PROCESSING_SERVICE;
    }

    public static DocumentPersistenceService documentPersistenceService() {
        return PERSISTENCE_SERVICE;
    }

    private static String requiredEnv(String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + name
            );
        }

        return value;
    }
}
